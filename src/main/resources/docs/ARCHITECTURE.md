# Template Service — Architecture

## Layers

```
api/                REST surface
  v1/                 public endpoints (TemplateController)
  internal/v1/        service-to-service endpoints (authenticated)
  advice/             GlobalExceptionHandler
  request/ response/  DTOs; response/error/ErrorResponse
  mapper/             REST DTO <-> application command/result

application/        Business logic
  port/in/            driving ports — what this service DOES
  port/out/           driven ports — what it NEEDS (Meta, waba-service, storage-service)
  usecase/            port/in implementations
  service/            supporting services (MediaSyncService, ...)
  dto/                command/, result/, client/
  mapper/ validation/

common/             Cross-cutting, depends on nothing above
  constant/           ApiPaths, ApiHeaders, InternalHeaders, LogKeys
  error/              ErrorCode
  exception/          exception types (not the HTTP translation)
  logging/            CorrelationIdFilter
  util/

domain/             Entities and enums. Depends on nothing.
  model/ enums/ repository/ service/

infrastructure/     Everything that talks to the outside world
  client/             facebook/, account/ (waba-service), media/ (storage-service)
  config/             beans + properties/
  security/           InternalApiAuthFilter
```

## Request path

```
CorrelationIdFilter        sets traceId/orgId/projectId in the MDC
  InternalApiAuthFilter    /internal/** only — shared-secret check
    Controller             no logic; delegates to a port.in
      UseCase              validation, persistence, Meta call
        port.out adapter   Meta Graph / waba-service / storage-service
```

## Serialization — the one thing to be careful about

This service's **server** side is snake_case
(`spring.jackson.property-naming-strategy: SNAKE_CASE`). Its **clients** talk
to waba-service and storage-service, which are camelCase.

`WebClientConfig` pins an explicit camelCase `ObjectMapper` on every outbound
client for exactly this reason. Previously that worked only by accident — a
bare `WebClient.builder()` bypasses Spring's context `ObjectMapper` and falls
back to Jackson defaults. Anyone injecting the auto-configured builder would
have silently switched decoding to snake_case, and since
`fail-on-unknown-properties` is off, every field would have deserialized to
`null` rather than throwing: access tokens and media URLs, silently absent.

## Outbound calls

| Upstream | Endpoint | Auth |
|---|---|---|
| waba-service | `GET /internal/v1/waba-credentials/by-waba/{wabaId}` | `X-Internal-Api-Key` |
| storage-service | `POST /api/v1/media/upload/batch` | `X-Internal-Api-Key` |
| Meta Graph | template CRUD, resumable media upload | per-WABA access token |

The internal key and the `X-Request-Id` correlation header are attached
centrally by `WebClientConfig`, not by individual adapters — an adapter that
has to remember is an adapter that will eventually forget. Meta gets neither:
leaking internal credentials outside the trust boundary is precisely what
those headers must not do.

## Frozen contract

`GET /api/v1/templates/{templateId}` is consumed by the **Messaging Service**
on the message-send path. Its path, its `X-Project-Id` header and its
`{status, message, data}` snake_case envelope are all fixed.

`GET /internal/v1/templates/{templateId}` is the authenticated equivalent,
returning a byte-identical body, so migrating is a base URL plus one header —
not a parsing change. Both delegate to the same use case and the same mapper,
so they cannot drift apart.

## Template sync

`POST /api/v1/templates/sync` returns `202` immediately and reconciles in the
background on the `mediaSyncExecutor` pool. Media download/upload is
best-effort: a failure leaves the template holding Meta's temporary handle
rather than aborting the sync and discarding metadata already fetched.

The pool uses `CallerRunsPolicy` — when the bounded queue fills, the
submitting thread runs the task, throttling the producer instead of
discarding work. For a sync, slower is correct and silently dropping media is
not.

## Known limitations

- **The internal API key is shared, not per-caller.** Any holder can assert
  any organisation.
- **Tenancy headers are self-asserted.** The gateway is responsible for
  populating them from an authenticated session on the public surface.
- **Sync work loses the MDC.** Work on the media-sync pool does not inherit
  the request's trace id, so the correlation header is omitted there rather
  than sent blank.
