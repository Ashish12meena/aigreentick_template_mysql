# Template Service — Architecture

Describes the service **as implemented**. If code and this file disagree, the
code wins and this file must be updated. Last reviewed: 2026-09-24.

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
  advice/              GlobalExceptionHandler, ApiErrorController (/error)
  request/ response/   DTOs (response/common: ApiEnvelope, PageResponse, Pagination, Responses; response/media/*)
  validation/          @OneOf constraint
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
  config/              WebClientConfig, MediaSyncThreadPoolConfig, CorsConfig, OpenApiConfig, WebMvcConfig, SchedulingConfig, properties/
  idempotency/         X-Idempotency-Key: filter, interceptor, store, IdempotencyRecord entity
  security/            InternalApiAuthFilter
common/              Cross-cutting: constant/, error/ (ErrorCode, FieldErrorCode), exception/, logging/, web/@Idempotent, util/helper/
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
RequestIdFilter            (HIGHEST_PRECEDENCE) X-Request-Id (validated or generated) + org/project/user → MDC; echoes X-Request-Id
  IdempotencyCachingFilter only POST with X-Idempotency-Key: buffers JSON body and response
    InternalApiAuthFilter  only for /internal/**; constant-time key check → 401 ApiEnvelope (UNAUTHENTICATED)
      IdempotencyInterceptor  only @Idempotent handlers: reserve key / replay stored response / 409
        Controller         validation annotations only; delegates to a port.in use case; wraps via Responses
          UseCase          validation, transactions, orchestration
            Query/Command service → Spring Data repositories → MySQL
            port.out adapter      → WebClient (+ request-context headers from MDC) → waba-service / storage-service / Meta
GlobalExceptionHandler     maps exceptions → ApiEnvelope error + HTTP status from ErrorCode
ApiErrorController         /error fallback (container-level failures) in the same wrapper
```

## 5. API surface

Public base: `/api/v1/templates`. All routes are constants in `ApiPaths`.

| Method | Path | Headers | Result | Use case |
|---|---|---|---|---|
| POST | `/api/v1/templates` | Org, Project, Waba, Idempotency-Key | 201 + `Location` | `CreateTemplateUseCase` |
| GET | `/api/v1/templates/{templateId}` | Org, Project | 200 | `GetTemplateUseCase.getById` — read by Messaging |
| GET | `/api/v1/templates/my-templates?page&size&sort&order&status&category&search` | Org, Project | 200 `{items, pagination}` | `GetTemplateUseCase.list` |
| GET | `/api/v1/templates/lookup?name=&language=` | Org, Project, Waba | 200 | `GetTemplateUseCase.getByNameAndLanguage` |
| PUT | `/api/v1/templates/{templateId}/draft` | Org, Project, Waba | 200 | `UpdateDraftTemplateUseCase` |
| POST | `/api/v1/templates/{templateId}/submit` | Org, Project, Idempotency-Key | 200 | `SubmitDraftToMetaUseCase` |
| DELETE | `/api/v1/templates/{templateId}?deleteFromMeta=` | Org, Project | 204 | `DeleteTemplateUseCase.deleteById` |
| DELETE | `/api/v1/templates` | Org, Project | 200 `{deletedCount}` | `DeleteTemplateUseCase.deleteAllByProject` |
| POST | `/api/v1/templates/sync` | Org, Project, Waba | 202 `{jobId, statusUrl}` | `SyncTemplateFromFacebookUseCase` |
| POST | `/api/v1/templates/media` (multipart `file`) | Org, Project, Waba, App | 200 | `WhatsappTemplateMediaUseCase` |
| GET | `/internal/v1/templates/{templateId}` | Org, Project, `X-Internal-Api-Key` | 200 | same as public getById |
| GET | `/internal/v1/templates/lookup` | Org, Project, Waba, `X-Internal-Api-Key` | 200 | same as public lookup |

Headers (`ApiHeaders`, API Standard §1): `X-Org-Id`, `X-Project-Id` (positive
longs, required); `X-User-Id` (optional); `X-Request-Id` (optional, validated
`[A-Za-z0-9._:-]{1,128}`, generated otherwise, always echoed — the only
tracking header); `X-Idempotency-Key` (create and submit); service-specific
`X-Waba-Id` (Meta WABA id) and `X-App-Id` (media upload only).
Internal (`InternalHeaders`): `X-Internal-Api-Key`, optional `X-Internal-Caller`.
A missing or unusable header is `400 BAD_REQUEST`.

**JSON naming:** this service's own API is **camelCase** (Jackson default; no
global naming strategy). Meta payloads are **snake_case** and are handled
separately (§8).

**Response wrapper** (`ApiEnvelope`, API Standard §4), for every JSON body:
`{success, status, code, message, data, errors, meta{requestId, timestamp, path?}}`.

- Built only through `Responses.ok/created/accepted/noContent` (controllers)
  and `ApiEnvelope.error` (handler, filters), which take one `HttpStatus` for
  both the response and the body, so `status` always equals the HTTP status.
  The wrapper rejects a non-2xx success or a 2xx error at construction.
- Success: `code: "SUCCESS"`, `errors: []`. Error: `data: null`, `meta.path` set.
- Lists: `data = PageResponse {items, pagination{page, size, totalItems, totalPages, hasNext, hasPrevious}}`.
- `@JsonInclude(ALWAYS)` on the wrapper overrides the global `non_null` so
  `data: null` and `errors: []` are always present.
- Documented wrapper exception: single delete returns `204` with no body.
- Meta rejection on create/submit is a 2xx: the template exists (status
  `FAILED`) and `data.errorMessage` / `data.errorPayload` explain why.

Actuator: `/actuator/health` (liveness/readiness probes enabled), `info`,
`metrics`; `prometheus` is additionally exposed in `prod`.
Swagger UI is disabled by default in `prod` (`SWAGGER_UI_ENABLED`).

## 6. Authentication and tenancy

- **Public `/api/v1/**`: no authentication in this service.** The gateway
  validates `Authorization: Bearer` and populates `X-Org-Id` / `X-Project-Id` /
  `X-User-Id` from the session (Spring Security is out of scope, rules.md §1.11).
  `X-Waba-Id` comes from the client. They are validated
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
  `X-Internal-Caller: template-service`, and passes on `X-Request-Id`,
  `X-Org-Id`, `X-Project-Id`, `X-User-Id` from the MDC (unless the adapter set
  one explicitly) to waba-service and storage-service calls centrally. Meta receives none of these — only the
  WABA access token (Bearer, `OAuth` header for resumable upload, or
  `access_token` query param for session start / offset).

## 7. Outbound integrations

All clients are built in `WebClientConfig`, one bean per upstream, each with
its own connect/read timeout and in-memory buffer limit, an explicit camelCase
`ObjectMapper`, and an error-logging filter.

| Upstream | Call | Adapter / port |
|---|---|---|
| waba-service | `GET {base}/internal/v1/waba-credentials/by-waba/{wabaId}` with `X-Org-Id`, `X-Project-Id` | `WabaCredentialAdapter` / `WabaCredentialPort` |
| storage-service | `POST {base}/api/v1/media/upload/batch` multipart, with `X-Org-Id`, `X-Project-Id`, `X-Waba-Id` and a fresh `X-Idempotency-Key` (required by storage); reads the standard wrapper only | `InternalMediaAdapter` / `InternalMediaPort` |
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

`GlobalExceptionHandler` is the only place exceptions become HTTP. Every
`BaseApplicationException` carries an `ErrorCode`, and each `ErrorCode` carries
its HTTP status, so one handler renders them all.

| Situation | HTTP | `code` | `errors[]` |
|---|---|---|---|
| Missing header, invalid header value, unparseable JSON | 400 | `BAD_REQUEST` | – |
| Missing or malformed `X-Idempotency-Key` on an `@Idempotent` endpoint | 400 | `IDEMPOTENCY_KEY_REQUIRED` | – |
| Bad internal API key (filter) | 401 | `UNAUTHENTICATED` | – |
| Unknown route | 404 | `NOT_FOUND` | – |
| `ResourceNotFoundException` (template) | 404 | `TEMPLATE_NOT_FOUND` | – |
| Wrong method | 405 | `METHOD_NOT_ALLOWED` (+ `Allow`) | – |
| `DuplicateResourceException` (template) | 409 | `TEMPLATE_ALREADY_EXISTS` | – |
| `DataIntegrityViolationException` | 409 | `CONFLICT` | – |
| `InvalidTemplateStateException` | 409 | `TEMPLATE_INVALID_STATE` | – |
| Idempotency key still running / reused for another request | 409 | `IDEMPOTENCY_KEY_IN_PROGRESS` / `IDEMPOTENCY_KEY_REUSED` | – |
| Upload too large | 413 | `PAYLOAD_TOO_LARGE` | – |
| Wrong Content-Type | 415 | `UNSUPPORTED_MEDIA_TYPE` | – |
| Bean validation (body, query, path), missing query param/part, bad enum or type in query/body | 422 | `VALIDATION_FAILED` | `REQUIRED`, `INVALID_VALUE`, `OUT_OF_RANGE`, `TOO_LONG`, `INVALID_FORMAT` |
| `TemplateRuleViolationException` | 422 | `VALIDATION_FAILED` | one per rule, `META_*` codes |
| anything else | 500 | `INTERNAL_ERROR` (generic message) | – |
| `WhatsappCredentialsNotFoundException` | 502 | `WABA_CREDENTIALS_UNAVAILABLE` | – |
| `MediaUploadException` | 502 | `MEDIA_UPLOAD_FAILED` | – |
| `ExternalServiceException`, untranslated `WebClientException` | 502 | `DEPENDENCY_FAILURE` | – |
| Any 502 whose cause chain holds a timeout | 504 | `TIMEOUT` | – |

Field names in `errors[]` are as sent: `template.components[0].text`, `size`.
Failures before the dispatcher (container errors) reach `ApiErrorController`
at `/error` and use the same wrapper.

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
api_idempotency_keys            (standalone; infrastructure.idempotency.IdempotencyRecord)
```

- `api_idempotency_keys` must be created by hand in prod (`ddl-auto: validate`);
  DDL is in the reference script.
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
  returns `{jobId, statusUrl}` where `jobId` is the request id.
- **Media re-hosting** downloads in parallel on the same pool, then uploads to
  storage-service in chunks bounded by `media-service.batch.max-bytes`
  (80 MB) and `max-files` (20). Best-effort: a failure leaves the component
  with no re-hosted `mediaUrl` and logs WARN/ERROR; the sync still persists.
- **MDC is propagated** to the pool by `MdcTaskDecorator`, so background logs
  carry the originating request id (the sync `jobId`) and background calls
  pass on `X-Request-Id` and tenancy headers.
- **Idempotency keys** live in `api_idempotency_keys` (org + project + key
  unique). Reserve/complete/release run in their own `REQUIRES_NEW`
  transactions; expired rows are purged hourly (`SchedulingConfig`).

## 12. Configuration and environments

- `application.yaml` (defaults), `application-dev.yaml`, `application-prod.yaml`;
  profile via `SPRING_PROFILES_ACTIVE` (default `dev`).
- All custom settings bind through `@ConfigurationProperties` classes in
  `infrastructure/config/properties`, registered in `PropertiesRegistrationConfig`:
  `internal.api.*`, `facebook-service.*`, `waba-service.*`, `media-service.*`,
  `media-sync.*`, `cors.*`, `idempotency.*`.
- `prod` requires env for datasource, `INTERNAL_API_KEY`, `CORS_ALLOWED_ORIGINS`,
  `EUREKA_DEFAULT_ZONE`; uses `ddl-auto: validate`, Hikari leak detection, and
  `server.forward-headers-strategy: framework`.
- Logs: console pattern includes `req`, `org`, `project`, `user` from the MDC.
