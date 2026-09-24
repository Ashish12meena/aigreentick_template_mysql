# Template Service — Project Memory

Running log of decisions, changes, fixes and open issues, written so a
developer or an AI assistant picking up this service knows what has already
been done and why. Newest entries first.

**How to maintain:** add an entry for every change that alters behaviour, a
contract, configuration, schema or a rule. Record *what* and *why* in a few
lines, and move items between "Known issues" and the log when they are fixed.
Keep `architecture.md`, `rules.md` and `prd.md` consistent in the same change.

---

## Standing decisions (read first)

| Decision | Detail |
|---|---|
| API JSON is camelCase | No global `property-naming-strategy`. Meta snake_case is handled only at the edge (`JsonHelper.serializeWithSnakeCase`, `FacebookJsonMapper`, `@JsonProperty`). Older comments claiming a snake_case contract were wrong and have been removed. |
| Time is `Instant`, stored in UTC | `DATETIME(6)` UTC; see `rules.md` §6. No `LocalDateTime` anywhere. |
| Entities are the schema source of truth | Hibernate `ddl-auto` manages tables; no Flyway/Liquibase. `db/migration/V1__initial_schema.sql` is a hand-kept reference only. |
| YAML defaults stay as they are | Hardcoded `${ENV:default}` fallbacks in `application.yaml` / `application-dev.yaml` are intentionally kept. |
| No broker, no cache, no Spring Security | Synchronous HTTP only; `/internal/**` guarded by `InternalApiAuthFilter`. |
| Company API Standard | Headers, wrapper `{success, status, code, message, data, errors, meta}`, status codes, pagination and error format follow the API Standard (adopted 2026-09-24). See rules.md §7. |
| Messaging read contract | `GET /api/v1/templates/{templateId}` and its `/internal` twin are read by the Messaging Service; path and `TemplateDetailResponseDto` must not change without that team. |
| Docs location | `src/main/resources/docs/` — `prd.md`, `architecture.md`, `rules.md`, `memory.md`. |

---

## Change log

### 2026-09-24 — Aligned with storage-service's API Standard migration
Both services now speak the same standard; these changes make template ↔
storage work in every environment, not just dev.
- **Filter renamed** `RequestContextFilter` → `RequestIdFilter`. A `@Component`
  named `RequestContextFilter` gets the bean name `requestContextFilter`, which
  Spring Boot already registers (`WebMvcAutoConfiguration`), so the context
  would fail to start. Same name as storage-service's filter now.
- **Storage auth header:** storage now authenticates `X-Internal-Api-Key` (it
  read only `X-Api-Key` before, so template's calls worked only with storage
  auth disabled). Ops: storage's `TEMPLATE_SERVICE_API_KEY` must equal this
  service's `INTERNAL_API_KEY`.
- **Batch upload sends `X-Idempotency-Key`** (fresh UUID per call):
  storage requires it on uploads and accepts no other name. storage's
  `data.results[]` shape and per-file codes are unchanged; the batch is now
  HTTP 200 (was 207).
- **Strictly standard, no legacy:** `StorageApiResponse` reads only the
  standard wrapper, and storage accepts only standard header names
  (`X-Internal-Api-Key`, `X-Idempotency-Key`, `X-Request-Id`).
- **New `ErrorCode.IDEMPOTENCY_KEY_REQUIRED`** (400) for a missing/malformed
  `X-Idempotency-Key`, the same code storage returns (was `BAD_REQUEST`).
- **Deploy both services together.** Neither side accepts the other's
  pre-standard shape or headers; a mismatched pair loses media re-hosting during
  sync (templates keep working but get no re-hosted `mediaUrl`).

### 2026-09-24 — API Standard adopted (headers, wrapper, status codes, pagination, errors)
Why: align this service with the company API Standard while the platform is
still in development. **No use-case, domain-rule or Meta logic changed**; the
work is at the HTTP edge (controllers, exception handling, filters, client
filters). Contract changes for consumers:

- **Wrapper:** `ResponseMessage` and `ErrorResponse` removed; every JSON
  response is `ApiEnvelope {success, status, code, message, data, errors,
  meta{requestId, timestamp, path?}}`. `status` is now the numeric HTTP status
  (was the string `"SUCCESS"`/`"ERROR"`); `errorCode`→`code`, `fieldErrors`→
  `errors` (`{field, code, message}`, no `rejectedValue`), `traceId`→
  `meta.requestId`. `domain.enums.ResponseStatus` removed.
  **Messaging Service must read `success`/HTTP status instead of `status == "SUCCESS"`.**
- **Status codes:** create → 201 + `Location` (idempotent replay → 200);
  single delete → 204 no body; bulk delete → 200 `{deletedCount}` (was
  `DeleteResponseDto` with `projectId`/`templateId`); sync → 202
  `{jobId, statusUrl}` (was `-1` counts); `InvalidTemplateStateException` 422 → 409.
- **Meta rejection** on create/submit: was 200 + `status: "ERROR"`; now a
  normal 2xx (`success: true`) with `data.status = FAILED` and
  `data.errorMessage`; the message says Meta did not accept it.
- **Errors:** `ErrorCode` rewritten to the standard vocabulary plus
  `<RESOURCE>_<PROBLEM>` codes, each bound to its HTTP status;
  `BaseApplicationException` now carries an `ErrorCode` instead of an
  `HttpStatus`. Header problems → 400 `BAD_REQUEST`; invalid field values
  (body, query, path, Meta rules) → 422 `VALIDATION_FAILED` with standard field
  codes (Meta rules keep `META_*`). Upstream timeouts → 504 `TIMEOUT`.
  `/error` answered by `ApiErrorController` in the same wrapper.
- **Pagination:** `sortBy`/`sortDir` → `sort`/`order`; default size 10 → 20;
  `sort` whitelisted (closes K3); `id` tie-breaker for stable pages; response
  `data = {items, pagination}` instead of a serialized Spring `Page`.
- **Headers:** `X-Org-Id` now required on every endpoint (was missing on
  get/list/submit/delete and internal reads). `X-User-Id` accepted.
  `CorrelationIdFilter` → `RequestIdFilter`: validates incoming
  `X-Request-Id` (else generates a UUID), also runs on error dispatch. MDC key
  `traceId` → `requestId`, plus `userId`; log pattern `[req= org= project= user=]`.
  Outbound calls to waba/storage now pass on `X-Org-Id`, `X-Project-Id`,
  `X-User-Id` from the MDC (adapter-set values win). `MdcTaskDecorator` on the
  sync pool (closes the MDC half of K9). CORS exposes `Location`, `Retry-After`.
- **Idempotency:** new `X-Idempotency-Key` support (`@Idempotent` on create
  and submit), required by default (`idempotency.required`, env
  `IDEMPOTENCY_REQUIRED`). New table `api_idempotency_keys` (reference DDL
  added; **create it by hand in prod**, which runs `ddl-auto: validate`).
  Hourly purge (`SchedulingConfig`). Same key + different request → 409
  `IDEMPOTENCY_KEY_REUSED`; still running → 409 `IDEMPOTENCY_KEY_IN_PROGRESS`.
- **Storage wrapper:** `StorageApiResponse` reads storage-service's standard
  wrapper (`success`, numeric `status`, `code`); no pre-standard shape.
- **Removed:** `TemplateResponseMapper.toListItem/toPage` (unused, entity-based).
- **Tests:** `ApiStandardContractTest` (WebMvc slice pinning the contract) and
  `ApiEnvelopeTest` (partially addresses K14).

### 2026-09-23 — Reference schema aligned with entities
- `db/migration/V1__initial_schema.sql` checked column-by-column against the
  entities and fixed: added `whatsapp_templates.meta_status_raw VARCHAR(64)`;
  unique key `uk_project_template (project_id, name, language)` →
  `uk_waba_template (waba_id, name, language)`; `status` ENUM gained `'UNKNOWN'`;
  `whatsapp_template_variables.label_value` `VARCHAR(500)` → `VARCHAR(255)`.
- Seeds and `template_reset.sql` verified: UTC timestamps, all columns exist, all 11 tables dropped.
- Closes K10.

### 2026-09-23 — Media upload uses the Meta app id (`X-App-Id`)
- Why: Meta's Resumable Upload API opens sessions on `POST /{APP_ID}/uploads`,
  but the service was sending the WABA id there (the adapter parameter was
  misnamed `wabaAppId`).
- Decision: the client sends the app id in a new header `X-App-Id` on
  `POST /api/v1/templates/media`. `X-Waba-Id` stays: it selects the access
  token from waba-service. Chosen over config / waba-service storage because
  organisations may use different Meta apps and the frontend already knows
  its app id; a wrong value is rejected by Meta and grants nothing.
- Code: `ApiHeaders.APP_ID`; `TemplateController.uploadMedia` (required
  `@NotBlank` header); `WhatsappTemplateMediaUseCase.uploadMedia(..., wabaId, appId)`;
  `FacebookMediaUploadPort.initiateUploadSession(..., appId, ...)`; adapter
  parameter renamed `wabaAppId` → `appId`.
- Frontend must send `X-App-Id` on media upload (missing → 400 `MISSING_PARAMETER`).
- Closes K5.

### 2026-09-23 — Mask Meta access tokens in logs
- Added `common/util/helper/SecretMasker` (`mask`, `maskUri`): keeps first and
  last 4 characters, e.g. `EAAG*****x9Qz`; values shorter than 12 → `*****`.
- `FacebookTemplateAdapter`: the "Initiating upload session" and "Checking
  upload offset" INFO lines now log the URL with `access_token` masked.
- `getUploadOffset`: a `WebClientResponseException` is rebuilt without the
  request URL (its message otherwise embeds `?access_token=...`), so the token
  cannot reach the global handler's stack trace. Same exception type and
  status, so behaviour is unchanged.
- Closes K4.

### 2026-09-23 — Documentation rewrite and comment cleanup
- Rewrote docs from the actual code: new `prd.md`, `memory.md`;
  `ARCHITECTURE.md` replaced by `architecture.md`; `rules.md` rewritten.
  The previous docs claimed a snake_case API and other things the code no longer does.
- Code comments corrected (no behaviour change): removed snake_case-contract
  claims from `TemplateController` (Javadoc + `@Tag`), `OpenApiConfig`,
  `ResponseMessage`, `ErrorResponse`, `ApiPaths`, `WebClientConfig`,
  `WabaCredentialsResponse`, and the Jackson comment in `application.yaml`
  (values untouched; the commented-out `SNAKE_CASE` line removed).
  Fixed references to the non-existent `FacebookJacksonConfig` (→ `FacebookJsonMapper`)
  and to `RULES.md` / `docs/rules.md` paths. Corrected `application/dto/package-info`.
- Removed commented-out code from `WhatsappTemplateMediaUseCaseImpl`,
  `WhatsappTemplateMapper` (old `applyMediaUrls` / `applyCarouselCardMediaUrl`),
  `MetaTemplateSubmissionService`, `MediaSyncService`.
- Removed the "What changed" history list from `TemplateController` Javadoc;
  its content is preserved below under "Earlier refactor".

### 2026-09-23 — `Instant` / UTC everywhere
- Why: timestamps were `LocalDateTime` (server-local, zone-less) in six
  entities and `Instant` in four; responses were ambiguous across time zones.
- Entities: `WhatsappTemplate` (`createdAt`, `updatedAt`, `deletedAt`),
  `WhatsappTemplateCarouselCard`, `WhatsappTemplateCarouselButton`,
  `WhatsappTemplateExample`, `WhatsappTemplateMediaUpload` (`createdAt`,
  `completedAt`), `WhatsappTemplateVariable` → `Instant`.
  `WhatsappTemplateVariable` switched from `@CreationTimestamp` to `@PrePersist`
  for consistency. `WhatsappTemplate.onCreate()` reads the clock once so
  `createdAt == updatedAt` on insert.
- Results/DTOs: `TemplateSummaryResult`, `TemplateDetailResult`,
  `TemplateResult`, `TemplateResponseDto`, `TemplateDetailResponseDto` → `Instant`.
- Soft delete: JPQL bulk updates take `:deletedAt` (`Instant.now()` from
  `TemplateCommandServiceImpl`) instead of DB `CURRENT_TIMESTAMP`;
  `@SQLDelete` uses `UTC_TIMESTAMP(6)`.
- Config (`application.yaml`): `hibernate.jdbc.time_zone: UTC`,
  `hibernate.type.preferred_instant_jdbc_type: TIMESTAMP`, Hikari
  `connectionTimeZone: UTC` + `forceConnectionTimeZoneToSession: true`,
  `spring.jackson.serialization.write-dates-as-timestamps: false`.
- SQL: reference schema timestamp columns → `DATETIME(6)` without DB defaults;
  seeds use `UTC_TIMESTAMP(6)`.
- API impact: detail endpoints (incl. `/internal`, read by the Messaging
  Service) now emit `"...Z"` UTC instead of zone-less local time.
- Dev databases created before this change must be reset
  (`template_reset.sql` → restart → seeds); old rows were written in local time.

### 2026-09-23 — `createdAt` / `updatedAt` on template responses
- `TemplateResponseDto` gained `createdAt`, `updatedAt`, populated for
  `my-templates` (via `TemplateSummaryResult`) and for create, update-draft
  and submit (via `TemplateResult`, including Meta-error responses).
- Added `TemplateCommandService.flush()`; called in `UpdateDraftTemplateUseCaseImpl`
  and `MetaTemplateSubmissionService` after the final status write, because
  `@PreUpdate` otherwise runs only at commit and the response would carry the
  old `updatedAt`. Same SQL statements, issued earlier; no extra writes.

### Earlier refactor (date not recorded — reconstructed from code comments)
- Routes centralised in `ApiPaths` (class mapping previously lacked a leading slash);
  a contradictory `TemplateConstants.Paths` block removed.
- Hand-rolled `GET /api/v1/templates/health` (always `UP`) removed in favour of Actuator.
- Tenancy headers validated (`@Positive`, `@NotBlank`); page size capped at 100;
  unused `X-Org-Id` removed from the list endpoint.
- `/internal/v1/templates` added with `InternalApiAuthFilter`; outbound calls now
  send `X-Internal-Api-Key` centrally (previously no key was sent to waba-service).
- `WebClientConfig` pins explicit camelCase mappers per upstream (previously correct only by accident).
- `FacebookJsonMapper` introduced after Meta parsing through the context mapper
  silently dropped `header_handle`, so sync collected no media.
- Credential DTO field `appId` renamed to `phoneNumberId`.
- `TemplateStatus.UNKNOWN` added so an unrecognised Meta status does not roll back a create.
- Unimplemented `TemplateAuditPort` and its DTOs removed; 18 empty packages removed;
  a duplicate port package and shadowed legacy `@Service` classes removed.
- Stable `ErrorCode` added to `ErrorResponse`; `ResponseMessage` factory methods
  replace hand-built envelopes.
- Sync made asynchronous (202) and split into phases so no DB connection is held
  during HTTP calls.

---

## Known issues (not fixed — confirm before changing behaviour)

| # | Issue | Where |
|---|---|---|
| K1 | `@Transactional` on `categorizeTemplates` / `persistChanges` has no effect (protected methods, self-invocation). Sync persistence is not atomic; each `saveAll`/soft-delete commits in `TemplateCommandServiceImpl`'s own transaction. **Accepted as-is (2026-09-23 decision): no change planned;** a later sync reconciles partial writes. | `SyncTemplateFromFacebookUseCaseImpl` |
| K2 | Meta rule validation runs only on create; update-draft and submit skip `TemplateValidationService`. | `UpdateDraftTemplateUseCaseImpl`, `SubmitDraftToMetaUseCaseImpl` |
| K6 | On a failed chunk upload the error uses `sessionResponse.getErrorMessage()` instead of the upload response's. | `WhatsappTemplateMediaUseCaseImpl` |
| K7 | `markAsSucceeded` / `markAsNewCreated` use `TemplateStatus.valueOf`, which throws on an unknown Meta status; `TemplateStatus.parse()` exists but is unused. | `TemplateCommandServiceImpl` |
| K8 | `metaStatusRaw`, `createdBy`, `qualityRating` are never set (quality stays `UNKNOWN`); `NEW_CREATED` is never assigned. | `WhatsappTemplate` |
| K9 | Sync result is only logged; there is no job-status endpoint, so the 202 `statusUrl` points at the template list. (MDC is now propagated; the `jobId` is the request id and tags every sync log line.) | sync |
| K16 | API Standard says delete of an already-deleted resource is 204; here it is 404 `TEMPLATE_NOT_FOUND`, because the soft-delete use case throws when no row matches. Left as-is to avoid changing use-case behaviour; decide with the frontend. | `TemplateCommandServiceImpl.softDeleteById` |
| K17 | The list path is `/my-templates`; a standard REST collection read would be `GET /api/v1/templates`. Not required by the API Standard; left unchanged. | `ApiPaths.TEMPLATE_LIST` |
| K11 | The reference SQL sits in `db/migration/` and begins with `drop schema`; if Flyway is ever added it would run automatically. | same |
| K12 | `application-dev.yaml` datasource default points at `apargo_wa_messaging` (another service's schema). Kept as-is by decision. | `application-dev.yaml` |
| K13 | Layering exceptions (do not add more): `application` imports `api.request` DTOs (commands wrap `BaseTemplateRequestDto`; validators, `WhatsappTemplateMapper`, `TemplateSyncMapper` read request DTOs; `SyncTemplateRequest` lives in `api.request`); media use case and `FacebookMediaUploadPort` return `api.response.media` types; `application` imports `infrastructure` (`MediaSyncThreadPoolConfig.MEDIA_SYNC_EXECUTOR`, `MediaServiceProperties`); `infrastructure` imports `api` (`ApiEnvelope` in `InternalApiAuthFilter`, media DTOs in `FacebookTemplateAdapter`). | various |
| K15 | `WhatsappTemplateVariablesRequestDto.labelValue` allows 500 chars (`@Size(max = 500)`) but the column is `length = 255`; a 256–500 char value passes validation and then fails at insert. | `WhatsappTemplateVariablesRequestDto`, `WhatsappTemplateVariable` |
| K14 | Only test is `contextLoads`; no unit or integration tests for use cases, validators or adapters. | `src/test` |

## Unused code (candidates for removal)

- `WhatsappTemplateMediaUpload` entity, `WhatsappTemplateMediaUploadRepository`,
  `WhatsappTemplateMediaServiceImpl`, table `whatsapp_template_media_uploads`
  (the save call in the media use case was removed earlier).
- `MediaUrlMappingRequestDto`, `MediaLocation` enum.
- `TemplateAuditEventType` enum.
- `TemplateCommandService.markAsSucceeded`.
- Unused import `ComponentFormat` in `WhatsappTemplateMapper`.

## Gotchas

- `CreateTemplateRequestDto.isDraft` serializes as JSON `"draft"` (Lombok boolean naming).
- `fail-on-unknown-properties: false` everywhere: wrong field names become `null`, not errors.
- `DELETE ...?deleteFromMeta=true` deletes on Meta by **name** (all languages).
- Sync never touches `DRAFT` templates; soft-deletes non-draft templates missing on Meta.
- `ddl-auto: update` does not change existing column types; reset dev DBs after type changes.
- No `pom.xml` was in the source archive received on 2026-09-23; the team's pom
  is Boot 3.5.6 / Java 21 / Spring Cloud 2025.0.0 / springdoc 2.8.13.
