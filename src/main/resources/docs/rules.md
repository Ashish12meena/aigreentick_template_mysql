# Template Service — Rules

MUST = required. NEVER = forbidden. Background lives in `architecture.md`; history and exceptions in `memory.md`.

## 1. Tech stack

1. MUST use Java 21, Maven, Spring Boot 3.5.6, compile with `-parameters`.
2. MUST use Spring MVC (servlet) for the HTTP API.
3. MUST use Spring Data JPA / Hibernate with MySQL for persistence.
4. MUST use the `WebClient` beans from `WebClientConfig` for every outbound call, called with `.block()`.
5. MUST use Jakarta Bean Validation for request validation and `TemplateValidationService` for Meta rules.
6. MUST use Lombok (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`, `@Getter/@Setter`).
7. MUST document endpoints with springdoc (`@Tag`, `@Operation`, `@Parameter`, `@ApiResponse`).
8. MUST use Actuator for health and metrics.
9. NEVER add Kafka, RabbitMQ or any message broker.
10. NEVER add Redis or any cache.
11. NEVER add Spring Security, JWT or OAuth2.
12. NEVER add Flyway or Liquibase.
13. NEVER use Feign, `RestTemplate`, `RestClient` or raw `HttpClient`.
14. NEVER add MapStruct or any mapping framework.
15. NEVER write WebFlux/reactive controllers.

## 2. Layers

1. `domain` MUST NOT import `api`, `application` or `infrastructure`.
2. `application` MUST reach external systems only through `application/port/out` interfaces.
3. `infrastructure` MUST implement `port/out` interfaces; it holds clients, config and filters.
4. `api` MUST call only `application/port/in` interfaces.
5. `api` NEVER uses entities, repositories or `*UseCaseImpl` classes directly.
6. `common` MUST NOT import any other layer.
7. NEVER add a new cross-layer import; existing ones are listed in `memory.md` (K13).

## 3. Classes and packages

1. MUST create one interface per use case in `application/port/in`.
2. MUST name its implementation `<Name>UseCaseImpl` in `application/usecase`.
3. MUST read via `TemplateQueryService` and write via `TemplateCommandService`.
4. MUST use constructor injection (`@RequiredArgsConstructor`, or an explicit constructor for `@Qualifier`).
5. NEVER use field `@Autowired`.
6. MUST write mappers as `@Component` classes named `*Mapper`.
7. NEVER keep empty packages.
8. NEVER add unused ports, DTOs, enums or classes "for later".
9. NEVER create a second copy of a port in another package.

## 4. Naming

1. MUST use one name per field across entity, DTO, repository and params.
2. `wabaId` MUST mean Meta's WABA id only.
3. NEVER use waba-service's `wabaAccountId` in place of `wabaId`.
4. `phoneNumberId` MUST mean Meta's Phone Number ID.
5. `appId` MUST mean Meta's App ID.
6. NEVER use `wabaId` as an app id.
7. MUST rename a misleading name instead of commenting around it.

## 5. JSON

1. MUST use camelCase for this service's API (requests and responses).
2. NEVER set `spring.jackson.property-naming-strategy`.
3. MUST use snake_case only for Meta:
   - send: `JsonHelper.serializeWithSnakeCase(...)`
   - receive: `FacebookJsonMapper.mapper()`
   - single fields: `@JsonProperty("meta_name")`
4. MUST keep camelCase for waba-service and storage-service clients.
5. NEVER register an `ObjectMapper` as a Spring bean.
6. MUST test new upstream DTOs with a real payload (`fail-on-unknown-properties` is off).
7. MUST remember Lombok boolean `isX` serializes as `x` (`isDraft` → `draft`).

## 6. Time

1. MUST use `java.time.Instant` for every timestamp.
2. NEVER use `LocalDateTime`, `LocalDate`, `Date` or `Timestamp`.
3. MUST store timestamps as UTC `DATETIME(6)`.
4. NEVER remove the UTC settings in `application.yaml` (`hibernate.jdbc.time_zone`, `preferred_instant_jdbc_type`, Hikari `connectionTimeZone`, `forceConnectionTimeZoneToSession`, `write-dates-as-timestamps`).
5. MUST set timestamps in Java (`@PrePersist`, `@PreUpdate`, or an `Instant` query parameter).
6. NEVER use `NOW()` or `CURRENT_TIMESTAMP` in queries.
7. MUST call `TemplateCommandService.flush()` before returning an entity whose `updatedAt` just changed.
8. MUST return timestamps as ISO-8601 UTC (`2026-09-23T09:12:10.123Z`).

## 7. API

The company API Standard is binding; these rules are how this service applies it.

1. MUST define every path in `ApiPaths`.
2. MUST define every header in `ApiHeaders` or `InternalHeaders`.
3. NEVER hard-code a path or header string in a controller.
4. MUST put public endpoints under `/api/v1`.
5. MUST put service-to-service endpoints under `/internal/v1`.
6. MUST take tenancy from headers (`X-Org-Id`, `X-Project-Id`, `X-Waba-Id`), never from path, query or body.
7. MUST require `X-Org-Id` and `X-Project-Id` on every business endpoint, and scope every data access by `projectId`.
8. MUST take the Meta app id from `X-App-Id` (media upload only).
9. MUST use `X-Request-Id` as the only tracking header. NEVER add `X-Correlation-Id`, `X-Trace-Id` or similar.
10. MUST validate at the controller (`@Valid`, `@NotNull`, `@Positive`, `@NotBlank`, `@Min`, `@Max`, `@OneOf`).
11. MUST paginate every list with `page` (from 0, default 0), `size` (1–`TemplateConstants.Defaults.MAX_SIZE`, default 20), `sort` (whitelisted with `@OneOf`) and `order` (`asc`/`desc`), and return `PageResponse` (`{items, pagination}`). NEVER return a Spring `Page` or a bare array.
12. NEVER put business logic in a controller: log, call the use case, wrap the result.
13. MUST build every success response with `Responses` (`ok`, `created` + `Location`, `accepted`, `noContent`) and pick the status from the standard's operation table.
14. MUST return "saved locally, rejected by Meta" as a 2xx whose `data` carries `status: FAILED` and `errorMessage`. NEVER return 200 with `success: false`.
15. MUST signal errors by throwing a `common.exception` type carrying an `ErrorCode`.
16. NEVER build an error body in a controller; `GlobalExceptionHandler` (or `ApiEnvelope.error` in a filter) does it.
17. MUST give every new error a stable `ErrorCode` with its HTTP status; resource-specific codes use `<RESOURCE>_<PROBLEM>`.
18. MUST use 400 `BAD_REQUEST` for unreadable bodies or headers and 422 `VALIDATION_FAILED` with `errors[]` for invalid field values.
19. MUST mark create/send endpoints `@Idempotent`.
20. MUST take messages from `TemplateConstants.Messages`. NEVER put SQL, stack traces or secrets in `message`.
21. NEVER change `GET /api/v1/templates/{templateId}` (path, headers, `TemplateDetailResponseDto`) without agreeing it with the Messaging Service team.
22. MUST keep `/internal/v1/templates/{templateId}` identical to the public one (same use case, same mapper).
23. MUST document any response outside the wrapper (e.g. `204`) in the endpoint's `@Operation`.

## 8. Database

1. Entities MUST be the source of truth for the schema.
2. MUST update `db/migration/V1__initial_schema.sql` in the same change as any entity change.
3. NEVER run that script automatically; it is reference only.
4. MUST use one schema for this service only.
5. NEVER hard-delete a `whatsapp_templates` row; soft-delete via `deleted_at`.
6. MUST use JPQL `@Query` with named `@Param`s.
7. MUST mark bulk updates `@Modifying`.
8. NEVER call an HTTP API inside a transaction.
9. MUST use `@Transactional(readOnly = true)` for reads.
10. MUST put `@Transactional` only on public methods called from another bean.
11. MUST keep `open-in-view: false`.

## 9. Outbound calls

1. MUST call external systems only through a `port/out` adapter in `infrastructure/client`.
2. MUST use the existing `WebClient` bean for that upstream.
3. NEVER create an ad-hoc `WebClient.builder()`.
4. NEVER add `X-Internal-Api-Key`, `X-Internal-Caller`, `X-Request-Id` or `X-User-Id` in an adapter; the client filters add them (tenancy headers are forwarded from the MDC unless the adapter sets them).
5. NEVER send internal headers to Meta.
6. MUST fetch the Meta access token from waba-service per operation.
7. NEVER store a Meta access token.
8. MUST wrap upstream failures in `ExternalServiceException`, `WhatsappCredentialsNotFoundException` or `MediaUploadException`.

## 10. Meta rule validation

1. MUST add new Meta rules to a validator under `application/validation`.
2. MUST collect all violations before throwing `TemplateRuleViolationException`.
3. MUST give each violation a dotted `field`, a `META_*` code and a message.
4. NEVER rename an existing `META_*` code.
5. NEVER add content-policy or subjective checks; Meta reviews those.

## 11. Configuration

1. MUST bind settings with `@ConfigurationProperties` in `infrastructure/config/properties`.
2. MUST register them in `PropertiesRegistrationConfig` and annotate `@Validated`.
3. NEVER use `@Value`.
4. MUST write values as `${ENV_VAR:default}`.
5. NEVER remove existing defaults in `application.yaml` / `application-dev.yaml`.
6. NEVER put secret defaults in `application-prod.yaml`.
7. NEVER set `ddl-auto: update` in prod.

## 12. Logging

1. MUST use `@Slf4j` with `{}` placeholders.
2. NEVER build log messages by string concatenation.
3. MUST keep MDC keys in `LogKeys`; `RequestIdFilter` owns the MDC, and every executor MUST use `MdcTaskDecorator`.
4. MUST log every lost result on best-effort paths at WARN or ERROR.
5. NEVER log a token or secret in clear text.
6. MUST mask tokens with `SecretMasker.mask(...)` and URLs with `SecretMasker.maskUri(...)`.
7. MUST strip or mask URLs with `access_token` from exception messages.

## 13. Code hygiene

1. NEVER commit commented-out code.
2. MUST write comments that explain why, not change history.
3. MUST update `prd.md`, `architecture.md` or `rules.md` in the same change that alters behaviour.
4. MUST add a `memory.md` entry for every behaviour, contract, config, schema or rule change.
