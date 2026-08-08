# WhatsApp Template Service — Project Overview

> Companion docs: [`RULES.md`](../../../../RULES.md) for architecture/naming
> rules, `V1__initial_schema.sql` for the fully-commented DB schema.

## Purpose

A Spring Boot 3.5.6 microservice (Java 21) that manages WhatsApp message
templates through Meta's Graph API. It's the bridge between the internal
platform (AiGreenTick) and Meta's WhatsApp Business API, handling the full
template lifecycle — creation, submission for approval, synchronization, and
deletion.

---

## What It Does

### Template Management
- **Create** templates locally as drafts or submit them directly to Meta for approval
- **Update** draft templates before submission (components, variables, metadata)
- **Submit** saved drafts to Meta's Graph API for review
- **Delete** templates locally (soft-delete) and optionally from Meta
- **List & Search** templates with filtering by status, category, and keyword search with pagination

### Facebook Sync
- Pulls all templates from a WhatsApp Business Account (WABA) via Meta's API
- Compares against locally stored templates to determine what's new, changed, or stale
- Inserts new templates, updates metadata (status, category, rejection reason) on existing ones, and soft-deletes templates no longer present on Meta's side
- Automatically extracts template variables from `{{n}}` placeholders during sync

### Media Upload
- Supports resumable media uploads to Meta (images, videos, documents)
- Implements a retry mechanism with offset recovery if the initial upload fails
- Tracks upload sessions and metadata in the database (`whatsapp_template_media_uploads`)

### Media Handling During Sync
- When syncing templates from Meta, header components (IMAGE/VIDEO/DOCUMENT) contain a `mediaHandle` — a Meta-internal reference to the media asset
- The service downloads the media from Meta using this `mediaHandle`, then re-uploads it to the internal Media Service with the file and headers (`X-Org-Id`, `X-Project-Id`, `X-Waba-Id`)
- The Media Service returns a publicly servable URL
- This URL is stored in the `mediaUrl` field on both `WhatsappTemplateComponent` (for normal templates) and `WhatsappTemplateCarouselCardComponent` (for carousel cards)
- This ensures all template media is served from internal infrastructure rather than relying on Meta's temporary handles

---

## Architecture

The project follows a **hexagonal (ports & adapters) structure**. Full rules
and the folder-by-folder responsibility breakdown live in `RULES.md` — this
is the short version:

```
api  →  application/port/in  ⇢ [implemented by] application/usecase
                                        ↓ uses
                                    domain/model, domain/repository, domain/service
                                        ↑ implements
application/port/out  ⇢ [implemented by]  infrastructure/*
```

- **`api/`** — REST controllers, request/response DTOs, and `api/mapper`
  (the only place that converts between the REST contract and the
  application layer's own command/result types). One controller currently
  handles all template endpoints under `/api/v1/templates`.
- **`application/`** — the business core:
  - `port/in` — one interface per use case (what this service can be asked to do)
  - `port/out` — what this service needs from the outside world (Meta Graph API, WABA credentials, media upload, audit)
  - `usecase` — one `*UseCaseImpl` class per `port/in` interface (Create, Update, Delete, Submit, Sync, Media Upload)
  - `dto/command`, `dto/result` — the application layer's own input/output shapes, kept separate from `api/dto`
  - `mapper` — domain entity ⇄ external payload (Meta JSON) translation
  - `service` — shared orchestration used by multiple use cases (e.g. `MetaTemplateSubmissionService`)
- **`domain/`** — JPA entities, enums, repository interfaces, domain services. The core business rules and data model — depends on nothing else in the project.
- **`infrastructure/`** — adapters implementing `application/port/out` (Meta Graph API client, WABA credential client, media client), plus Spring configuration (Jackson, WebClient, Hibernate JSON format mapper).
- **`common/`** — shared utilities, constants, and the global exception handler.

> Not every use case has been fully migrated to the command/result pattern
> yet — `CreateTemplateUseCase`, `UpdateDraftTemplateUseCase`,
> `GetTemplateUseCase`, and `SubmitDraftToMetaUseCase` have been; sync and
> media-upload use cases still reference `api/dto` types directly. See
> RULES.md for the target pattern to follow when migrating the rest.

---

## Data Model

The template data model is deeply nested to match Meta's template structure:

- **WhatsappTemplate** — Root entity with metadata (name, category, language, status, `wabaId`, Meta template ID)
      - **WhatsappTemplateComponent** — HEADER, BODY, FOOTER, BUTTONS, CAROUSEL, or LIMITED_TIME_OFFER. Includes `mediaHandle` (Meta's internal reference) and `mediaUrl` (internal servable URL after re-upload)
    - **WhatsappTemplateButton** — URL, QUICK_REPLY, PHONE_NUMBER, OTP, etc.
      - **WhatsappTemplateButtonSupportedApp** — Android app details for OTP autofill
    - **WhatsappTemplateExample** — Sample values for Meta's template validation
    - **WhatsappTemplateCarouselCard** — Individual cards within a carousel
      - **WhatsappTemplateCarouselCardComponent** — Header/Body/Buttons within each card, also carries `mediaHandle` and `mediaUrl` for card-level media
        - **WhatsappTemplateCarouselButton** — Buttons specific to carousel cards
        - **WhatsappTemplateCarouselExample** — Examples for carousel card components
  - **WhatsappTemplateVariable** — Variable metadata with a composite key (component type, variable index, button index, card index) for precise identification
- **WhatsappTemplateMediaUpload** — Tracks resumable media upload sessions (standalone table, not FK-linked to a template)

All entities use JPA cascade operations and orphan removal for clean
lifecycle management. Templates use soft-delete via a `deleted_at`
timestamp with Hibernate's `@SQLDelete` and `@SQLRestriction` — see
RULES.md, never issue a hard `DELETE` against `whatsapp_templates`.

**Naming note:** the external Meta/WhatsApp Business Account identifier is
called `wabaId` (String) consistently across the entity, DTOs, and DB
columns. Earlier revisions of this schema used `wabaAccountId` in one table
and `waba_account_id` (as a numeric FK) in another — both were renamed to
`wabaId` for consistency. See `V1__initial_schema.sql` for the current,
fully-commented column definitions.

---

## Key Design Decisions

1. **Soft Deletes Everywhere** — Templates are never physically deleted; a `deleted_at` column is set, and Hibernate filters them automatically.

2. **Dual Mapper Strategy** — `WhatsappTemplateMapper` handles user-created templates (from the API), while `TemplateSyncMapper` handles Facebook-synced templates with automatic variable extraction from `{{n}}` placeholders.

3. **Shared Submission Logic** — `MetaTemplateSubmissionService` is used by both create and submit-draft flows to avoid duplicating Meta API interaction code.

4. **Command/Query Repository Split** — Write operations (soft-delete, bulk mutations) live in `WhatsappTemplateCommandRepository`, while all read operations live in `WhatsappTemplateQueryRepository`.

5. **Command/Query Service Split** — Mirrors the repository split at the service level, with `TemplateCommandService` for writes and `TemplateQueryService` for reads.

6. **Ports & Adapters for every external dependency** — Meta Graph API, WABA credentials, and the media service are all reached through `application/port/out` interfaces, implemented by adapters in `infrastructure/client`. Use cases never hold an HTTP client directly.

7. **Global snake_case JSON** — `spring.jackson.property-naming-strategy: SNAKE_CASE` is set globally in `application.yaml`, so the entire API surface (not just the Meta submission payload) serializes as snake_case. `CustomJackson3JsonFormatMapper` in `infrastructure/config` is unrelated dead code — see "Current Limitations."

8. **Two separate snake_case mechanisms** — `common/util/helper/JsonHelper.serializeWithSnakeCase(...)` uses its own dedicated `ObjectMapper` (with `PropertyNamingStrategies.SNAKE_CASE`) purely to build the `submissionPayload` string sent to Meta's Graph API. This is separate from — and redundant with — the global `spring.jackson.property-naming-strategy: SNAKE_CASE` setting in `application.yaml`, which governs the REST API's own request/response bodies. Both happen to be snake_case today, but they're two independent mappers for two different purposes; don't assume changing one affects the other.

9. **Eureka service discovery** — the service registers itself with a Eureka server on startup (`spring-cloud-starter-netflix-eureka-client`), so it's discoverable by other services in the platform rather than called via a hardcoded host/port.

---

## External Integrations

- **Meta Graph API** (v23.0) — Template CRUD, media upload sessions, and template sync, called through `application/port/out/FacebookTemplatePort` / `FacebookTemplateSyncPort` / `FacebookMediaUploadPort`, implemented by `infrastructure/client/facebook/FacebookTemplateAdapter` using Spring WebFlux's `WebClient`
- **Internal Media Service** — Accepts file uploads with org/project/WABA context and returns a permanent servable URL. Used during sync to re-host Meta media assets internally. Reached via `application/port/out/InternalMediaPort` → `infrastructure/client/media/InternalMediaAdapter`.
- **WABA Credential Service** — Resolves WABA access tokens via `application/port/out/WabaCredentialPort` → `infrastructure/client/account/WabaCredentialAdapter` (see Technical Debt — currently returns hardcoded values pending the real service integration)

---

## Tech Stack

- **Framework:** Spring Boot 3.5.6 (with Spring Cloud 2025.0.0)
- **Language:** Java 21
- **Service Discovery:** Netflix Eureka client — registers with a Eureka server at startup
- **Database:** MySQL with Hibernate 6 / Spring Data JPA (standard Jackson 2, `com.fasterxml.jackson` — see note below)
- **HTTP Client:** Spring WebFlux WebClient (for non-blocking Meta API calls)
- **JSON:** Jackson 2 with a **global** snake_case naming strategy (`spring.jackson.property-naming-strategy: SNAKE_CASE` in `application.yaml` — this applies to the whole API, not just the Meta submission payload)
- **API Docs:** springdoc-openapi (Swagger UI)
- **Build:** Maven with Lombok for boilerplate reduction
- **Environment:** spring-dotenv for `.env` file support

> **Correction:** an earlier version of this document (and this doc's own
> predecessor, `Overview.txt`) claimed Spring Boot 4.0.2 with Jackson 3 +
> Hibernate 7, and described a dedicated snake_case serializer used only
> for the Facebook submission payload. Neither was accurate for the actual
> `pom.xml` in use — this project runs Spring Boot 3.5.6 (standard Jackson
> 2 / Hibernate 6), and the snake_case naming strategy is global. See
> "Current Limitations" below for the dead code this left behind.

---

## Current Limitations & Technical Debt

- **`CustomJackson3JsonFormatMapper` is dead code** — every method body is commented out; it was written in anticipation of a Spring Boot 4 + Jackson 3 migration that hasn't happened. The actual `pom.xml` uses Spring Boot 3.5.6, which needs no such bridge. Either delete this class or clearly mark it as a future-migration placeholder — as it stands, it misleads anyone reading it into thinking Jackson 3 is in use.
- **`V1__initial_schema.sql` is not actually wired to Flyway** — the filename follows Flyway's naming convention (`V{n}__description.sql` under `db/migration/`), but there's no `flyway-core` or `spring-boot-starter-flyway`(now `-jdbc`) dependency in `pom.xml`, and no Flyway config in `application.yaml`. With `hibernate.ddl-auto: validate`, the schema has to be applied to the database by some other means (manually, a DBA process, or a separate tool) — this isn't automated on app startup the way the filename suggests. Either add the Flyway dependency to make it real, or rename the file so it doesn't imply a migration tool that isn't there. (`CriticalIssue.txt` flagged this correctly — worth checking that file for other still-valid items before assuming everything in it is stale.)
- **Hardcoded credentials** in `WabaCredentialAdapter` — should be replaced with actual service calls or externalized configuration
- **Database password has a plaintext default value in `application.yaml`** — `${SPRING_DATASOURCE_PASSWORD:...}` falls back to a real-looking password if the env var isn't set. Remove the default entirely (fail fast if the env var is missing) rather than shipping a fallback secret in version control.
- **No authentication/authorization** — relies on header-based identity (`X-Project-Id`, `X-Organization-Id`) without validation
- **Media upload persistence not wired** — `WhatsappTemplateMediaUseCase` has the resumable-upload flow, but saving the `WhatsappTemplateMediaUpload` record is commented out; the table exists but stays empty in practice
- **No retry/circuit-breaker** on Meta API calls — fallback methods exist but aren't wired to any resilience framework
- **Sync fetches all templates in one call** — no pagination handling for large WABA accounts
- **Several use cases still depend on `api/dto` directly** (`SyncTemplateFromFacebookUseCase`, `WhatsappTemplateMediaUseCase`) — not yet migrated to the `application/dto/command` + `application/dto/result` pattern used by the rest; see RULES.md for the pattern to follow
- **Several scaffold packages are still empty** (e.g. `domain/exception`, `domain/factory`, `common/aspect`, `infrastructure/persistence`) — either fill these with real classes when the need arises, or remove them; see RULES.md, "no empty packages"
- **`AccessTokenIdentifier` / `FacebookApiResponse` live under `api/dto/response/client`** despite being external-client response shapes rather than REST contract types — candidates to move into `infrastructure` or `application` in a future cleanup

---

## Resolved Since Last Revision of This Doc

- ~~No global exception handler~~ — `common/exception/GlobalExceptionHandler.java` now exists and centralizes exception → HTTP response mapping.
- ~~`waba_account_id` naming inconsistency~~ — unified to `wabaId` across the schema and codebase.
- ~~Use cases were concrete classes injected directly into the controller~~ — the controller now depends on `application/port/in` interfaces, with `*UseCaseImpl` classes as the only implementation.