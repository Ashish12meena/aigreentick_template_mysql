# Template Service — Architecture

Describes the service **as implemented**. If code and this file disagree, the
code wins and this file must be updated. Last reviewed: 2026-09-23.

## 1. What this service is

`template-service` owns WhatsApp message templates for the Aigreentick /
Apargo platform: creating and validating them, submitting them to Meta for
review, syncing their review status back from Meta, and serving them to other
services (notably the Messaging Service, which reads a template before
sending a message).

| | |
|---|---|
| Runtime | Java 21, Spring Boot 3.5.6 (servlet / Spring MVC) |
| Artifact | `com.aigreentick.services:template` → `spring.application.name: template-service` |
| Port | `8080` (`SERVER_PORT`) |
| Database | MySQL, own schema (`apargo_wa_template` by default) via Spring Data JPA / Hibernate |
| Discovery | Netflix Eureka client (Spring Cloud 2025.0.0) |
| Outbound HTTP | Spring `WebClient` (Reactor Netty), used **blocking** (`.block()`) |
| API docs | springdoc-openapi 2.8.13 (`/v3/api-docs`, `/swagger-ui.html`) |
| Health / metrics | Spring Boot Actuator |

### Not part of this service

These are **not** used here. Do not assume them when reading or changing the code:

- **Kafka / RabbitMQ / any message broker.** All integration is synchronous HTTP.
- **Redis / any cache.** Nothing is cached; WABA credentials are fetched per operation.
- **Spring Security, JWT, OAuth2 resource server.** See §6.
- **Flyway / Liquibase.** Present in `pom.xml` only as a commented-out dependency.
- **Feign, Resilience4j, Spring Retry.** No declarative clients, circuit breakers or retry policies.
- **WebFlux server.** `spring-boot-starter-webflux` is on the classpath only for `WebClient`.

## 2. System context

```
                    ┌──────────────────────────┐
  Frontend / ──────▶│  API gateway (external)  │  sets X-Org-Id / X-Project-Id / X-Waba-Id
  dashboard         └────────────┬─────────────┘
                                 │  /api/v1/**
 Messaging Service ──────────────┤  GET /api/v1/templates/{id}   (frozen contract)
                                 │  GET /internal/v1/templates/** (X-Internal-Api-Key)
                                 ▼
                   ┌──────────────────────────────┐        ┌─────────────┐
                   │       template-service       │───────▶│   MySQL     │
                   │  (registers with Eureka)     │  JPA   │ own schema  │
                   └──┬─────────────┬─────────────┘        └─────────────┘
                      │             │             │
     X-Internal-Api-Key            │             │  Bearer / OAuth <waba token>
                      ▼             ▼             ▼
              ┌─────────────┐ ┌──────────────┐ ┌────────────────────┐
              │ waba-service│ │storage-service│ │ Meta Graph API      │
              │ credentials │ │ media re-host │ │ graph.facebook.com  │
              └─────────────┘ └──────────────┘ └────────────────────┘
```

| Peer | Direction | Purpose |
|---|---|---|
| API gateway / frontend | inbound | All `/api/v1/**` calls. Gateway is trusted to populate tenancy headers. |
| Messaging Service | inbound | Reads templates by id (public frozen endpoint, or the `/internal` twin). |
| waba-service | outbound | Resolve a WABA's access token (and phone number id) per request. |
| storage-service | outbound | Batch upload of media downloaded from Meta during sync. |
| Meta Graph API | outbound | Create / list / delete message templates; resumable media upload. |
| Eureka | registration | Service registers itself; outbound clients use **configured base URLs**, not Eureka lookup. |

## 3. Code structure (hexagonal / ports-and-adapters)

Root package: `com.aigreentick.services.template`

```
api/                 HTTP boundary
  v1/                  TemplateController           (/api/v1/templates)
  internal/v1/         InternalTemplateController   (/internal/v1/templates)
  advice/              GlobalExceptionHandler
  request/ response/   DTOs (response/error/ErrorResponse, response/media/*)
  mapper/              API DTO <-> application command/result
application/         Use cases and orchestration
  port/in/             driving ports (one interface per use case)
  port/out/            driven ports (Meta, waba-service, storage-service)
  usecase/             *UseCaseImpl implementing port/in
  service/             MetaTemplateSubmissionService, MediaSyncService, FacebookMediaDownloadService
  validation/          TemplateValidationService + 5 rule validators
  mapper/              request/Meta payload -> entity, entity -> result
  dto/                 command/, result/, client/, TenantScope, ...
domain/              Persistence model
  model/               11 JPA entities
  enums/               TemplateStatus, TemplateCategory, ComponentType, ...
  repository/          Query (reads) and Command (writes) repositories split
  service/             TemplateQueryService / TemplateCommandService (+ impl/)
infrastructure/      Adapters and wiring
  client/              facebook/ (FacebookTemplateAdapter), account/ (waba-service), media/ (storage-service)
  config/              WebClientConfig, MediaSyncThreadPoolConfig, CorsConfig, OpenApiConfig, properties/
  security/            InternalApiAuthFilter
common/              Cross-cutting: constant/, error/ErrorCode, exception/, logging/, util/helper/
```

Dependency direction actually in the code (see `memory.md` K13 for the known exceptions):

```
api ──▶ application ──▶ domain
             ▲
infrastructure (implements application/port/out)
common ◀── everyone
```

## 4. Request path

```
CorrelationIdFilter        (HIGHEST_PRECEDENCE) X-Request-Id → MDC traceId, + orgId/projectId; echoes X-Request-Id
  InternalApiAuthFilter    only for /internal/**; constant-time key check → 401 ErrorResponse
    Controller             validation annotations only; delegates to a port.in use case
      UseCase              validation, transactions, orchestration
        Query/Command service → Spring Data repositories → MySQL
        port.out adapter      → WebClient → waba-service / storage-service / Meta
GlobalExceptionHandler     maps exceptions → ErrorResponse + HTTP status
```

## 5. API surface

Public base: `/api/v1/templates`. All routes are constants in `ApiPaths`.

| Method | Path | Headers | Use case |
|---|---|---|---|
| POST | `/api/v1/templates` | Project, Org, Waba | `CreateTemplateUseCase` |
| GET | `/api/v1/templates/{templateId}` | Project | `GetTemplateUseCase.getById` — **frozen** |
| GET | `/api/v1/templates/my-templates` | Project | `GetTemplateUseCase.list` |
| GET | `/api/v1/templates/lookup?name=&language=` | Project, Waba | `GetTemplateUseCase.getByNameAndLanguage` |
| PUT | `/api/v1/templates/{templateId}/draft` | Project, Org, Waba | `UpdateDraftTemplateUseCase` |
| POST | `/api/v1/templates/{templateId}/submit` | Project | `SubmitDraftToMetaUseCase` |
| DELETE | `/api/v1/templates/{templateId}?deleteFromMeta=` | Project | `DeleteTemplateUseCase.deleteById` |
| DELETE | `/api/v1/templates` | Project | `DeleteTemplateUseCase.deleteAllByProject` |
| POST | `/api/v1/templates/sync` | Project, Org, Waba | `SyncTemplateFromFacebookUseCase` (202) |
| POST | `/api/v1/templates/media` (multipart `file`) | Project, Org, Waba, App | `WhatsappTemplateMediaUseCase` |
| GET | `/internal/v1/templates/{templateId}` | Project, `X-Internal-Api-Key` | same as public getById |
| GET | `/internal/v1/templates/lookup` | Project, Waba, `X-Internal-Api-Key` | same as public lookup |

Headers (`ApiHeaders`): `X-Org-Id`, `X-Project-Id` (positive longs), `X-Waba-Id`
(Meta WABA id, string), `X-App-Id` (Meta app id; media upload only),
`X-Request-Id` (optional correlation id).
Internal (`InternalHeaders`): `X-Internal-Api-Key`, optional `X-Internal-Caller`.

**JSON naming:** this service's own API is **camelCase** (Jackson default; no
global naming strategy). Meta payloads are **snake_case** and are handled
separately (§8).

**Envelopes:**

- Success: `ResponseMessage {status: "SUCCESS"|"ERROR", message, data}`.
  `"ERROR"` with HTTP 200 is used only for *partial failure* on create/submit
  (saved locally, rejected by Meta).
- Error: `ErrorResponse {status, code, errorCode, message, path, timestamp, traceId, fieldErrors[]}`.
  `errorCode` is a stable `ErrorCode` enum value; clients branch on it, never on `message`.

Actuator: `/actuator/health` (liveness/readiness probes enabled), `info`,
`metrics`; `prometheus` is additionally exposed in `prod`.
Swagger UI is disabled by default in `prod` (`SWAGGER_UI_ENABLED`).

## 6. Authentication and tenancy

- **Public `/api/v1/**`: no authentication in this service.** Tenancy comes
  from `X-Org-Id` / `X-Project-Id` / `X-Waba-Id`, which the gateway is
  responsible for populating from an authenticated session. They are validated
  (`@Positive`, `@NotBlank`) but not verified.
- **Every query is scoped by `projectId`** (and by `wabaId` where relevant),
  so a caller can only reach templates of the project it asserts.
- **`/internal/**`:** `InternalApiAuthFilter` (plain `OncePerRequestFilter`, no
  Spring Security) compares `X-Internal-Api-Key` with `internal.api.api-key`
  using `MessageDigest.isEqual`. Enabled by `internal.api.auth-enabled`
  (`false` in default/dev, `true` in prod). The key is **shared**, not per
  caller. `internal.api.path-prefix` must equal `ApiPaths.INTERNAL` — checked
  at startup.
- **Outbound:** `WebClientConfig` attaches `X-Internal-Api-Key`,
  `X-Internal-Caller: template-service` and `X-Request-Id` to waba-service and
  storage-service calls centrally. Meta receives none of these — only the
  WABA access token (Bearer, `OAuth` header for resumable upload, or
  `access_token` query param for session start / offset).

## 7. Outbound integrations

All clients are built in `WebClientConfig`, one bean per upstream, each with
its own connect/read timeout and in-memory buffer limit, an explicit camelCase
`ObjectMapper`, and an error-logging filter.

| Upstream | Call | Adapter / port |
|---|---|---|
| waba-service | `GET {base}/internal/v1/waba-credentials/by-waba/{wabaId}` with `X-Org-Id`, `X-Project-Id` | `WabaCredentialAdapter` / `WabaCredentialPort` |
| storage-service | `POST {base}/api/v1/media/upload/batch` multipart, with `X-Org-Id`, `X-Project-Id`, `X-Waba-Id` | `InternalMediaAdapter` / `InternalMediaPort` |
| Meta | `POST /{ver}/{wabaId}/message_templates` (create) | `FacebookTemplateAdapter` / `FacebookTemplatePort` |
| Meta | `GET /{ver}/{wabaId}/message_templates?limit=200&after=` (list, paginated) | `FacebookTemplateAdapter` / `FacebookTemplateSyncPort` |
| Meta | `DELETE /{ver}/{wabaId}/message_templates?name=` | `FacebookTemplateAdapter` / `FacebookTemplatePort` |
| Meta | `POST /{ver}/{appId}/uploads` (appId from `X-App-Id`), `POST /{ver}/{sessionId}`, `GET /{ver}/{sessionId}` (resumable upload) | `FacebookTemplateAdapter` / `FacebookMediaUploadPort` |
| Meta CDN | plain `URL.openStream()` download of header media during sync | `FacebookMediaDownloadService` |

Defaults (`application.yaml`): Meta `https://graph.facebook.com`, API version
`v23.0`; waba-service and storage-service base URLs are environment-overridable.
No retries or circuit breakers; failures surface as exceptions (§9) except
where noted as best-effort.

## 8. Serialization

| Where | Naming | How |
|---|---|---|
| This service's HTTP API (in and out) | camelCase | Spring Boot's auto-configured `ObjectMapper` (`fail-on-unknown-properties: false`, `default-property-inclusion: non_null`, ISO-8601 dates) |
| waba-service / storage-service clients | camelCase | explicit mapper per client in `WebClientConfig` |
| Meta request body (create) | snake_case | `JsonHelper.serializeWithSnakeCase(BaseTemplateRequestDto)`; stored in `submission_payload` and reused on submit |
| Meta responses (sync list) | snake_case | `FacebookJsonMapper.mapper()` binds `JsonNode` → `SyncTemplateRequest` |
| Meta media responses | explicit names | `@JsonProperty` (`id`, `h`, `file_offset`) |

`FacebookJsonMapper` and `JsonHelper` hold private static mappers and must
never be exposed as Spring beans (that would make Boot's `ObjectMapper`
auto-configuration back off for the whole API).

## 9. Error handling

`GlobalExceptionHandler` is the only place exceptions become HTTP.

| Exception | HTTP | `errorCode` |
|---|---|---|
| Bean validation (body, params, headers) | 400 | `VALIDATION_FAILED` |
| Unreadable / malformed body, type mismatch | 400 | `INVALID_REQUEST` / `MALFORMED_REQUEST_BODY` |
| Missing param or header | 400 | `MISSING_PARAMETER` |
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| Unknown route | 404 | `ENDPOINT_NOT_FOUND` |
| Wrong method | 405 | `METHOD_NOT_ALLOWED` |
| `DuplicateResourceException`, `DataIntegrityViolationException` | 409 | `DUPLICATE_RESOURCE` |
| `InvalidTemplateStateException` | 422 | `INVALID_TEMPLATE_STATE` |
| `TemplateRuleViolationException` (all violations listed in `fieldErrors`) | 422 | `TEMPLATE_RULE_VIOLATION` |
| Upload too large | 413 | `PAYLOAD_TOO_LARGE` |
| `WhatsappCredentialsNotFoundException` | 502 | `UPSTREAM_CREDENTIAL_UNAVAILABLE` |
| `MediaUploadException` | 502 | `UPSTREAM_MEDIA_UPLOAD_FAILED` |
| `ExternalServiceException` | 502 | `UPSTREAM_META_API_FAILED` |
| anything else | 500 | `INTERNAL_ERROR` |
| bad internal key (filter) | 401 | `UNAUTHORIZED` |

## 10. Data model

MySQL, one schema per service. **The JPA entities are the source of truth.**
Tables are created/updated by Hibernate (`ddl-auto: update` by default,
`validate` in prod). `db/migration/V1__initial_schema.sql` is a hand-maintained
reference script, **not** run by any tool.

```
whatsapp_templates (root, soft-deleted)
 ├─ whatsapp_template_components        (HEADER/BODY/FOOTER/BUTTONS/CAROUSEL/LIMITED_TIME_OFFER)
 │   ├─ whatsapp_template_examples          (1:1, JSON header/body examples)
 │   ├─ whatsapp_template_buttons
 │   │   └─ whatsapp_template_button_supported_apps
 │   ├─ whatsapp_template_carousel_examples (1:1)
 │   └─ whatsapp_template_carousel_cards
 │       └─ whatsapp_template_carousel_card_components
 │           └─ whatsapp_template_carousel_buttons
 └─ whatsapp_template_variables
whatsapp_template_media_uploads (standalone; currently unused — see memory.md)
```

- Children are owned via `cascade = ALL, orphanRemoval = true`; replacing a
  draft's components clears and re-adds them.
- `whatsapp_templates` unique key `uk_waba_template (waba_id, name, language)`.
- Soft delete: `deleted_at` + `@SQLRestriction("deleted_at IS NULL")` +
  `@SQLDelete`. Bulk deletes are JPQL updates that set `deletedAt`.
- **Time:** every timestamp is `java.time.Instant`, stored as `DATETIME(6)` in
  UTC (`hibernate.jdbc.time_zone: UTC`, `preferred_instant_jdbc_type:
  TIMESTAMP`, JDBC session forced to UTC). Set by `@PrePersist`/`@PreUpdate`.
- Dev/test helper scripts in `db/`: `template_reset.sql`, `template_seed.sql`,
  `template_p_seed.sql`.

## 11. Concurrency and background work

- **Template sync** returns `202` immediately and runs on the
  `mediaSyncExecutor` pool (`MediaSyncThreadPoolConfig`: fixed size
  `media-sync.pool-size` = 15, queue 100, `CallerRunsPolicy`, graceful
  shutdown wait 30 s). Phases: fetch all pages from Meta → categorize against
  DB → download/re-host media → persist. Outcome is only logged; the API
  returns placeholder counts (`-1`).
- **Media re-hosting** downloads in parallel on the same pool, then uploads to
  storage-service in chunks bounded by `media-service.batch.max-bytes`
  (80 MB) and `max-files` (20). Best-effort: a failure leaves the component
  with no re-hosted `mediaUrl` and logs WARN/ERROR; the sync still persists.
- **MDC is not propagated** to the pool, so background calls carry no
  `X-Request-Id`.

## 12. Configuration and environments

- `application.yaml` (defaults), `application-dev.yaml`, `application-prod.yaml`;
  profile via `SPRING_PROFILES_ACTIVE` (default `dev`).
- All custom settings bind through `@ConfigurationProperties` classes in
  `infrastructure/config/properties`, registered in `PropertiesRegistrationConfig`:
  `internal.api.*`, `facebook-service.*`, `waba-service.*`, `media-service.*`,
  `media-sync.*`, `cors.*`.
- `prod` requires env for datasource, `INTERNAL_API_KEY`, `CORS_ALLOWED_ORIGINS`,
  `EUREKA_DEFAULT_ZONE`; uses `ddl-auto: validate`, Hikari leak detection, and
  `server.forward-headers-strategy: framework`.
- Logs: console pattern includes `trace`, `org`, `project` from the MDC.
