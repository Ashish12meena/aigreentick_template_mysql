# Template Service — Product Requirements Specification

Describes what the service does **today**, derived from the implementation.
Items marked *(inferred)* are reasoned from code, not from a written business
spec; confirm them with the product owner. Last reviewed: 2026-09-23.

## 1. Purpose

WhatsApp Business messages outside the 24-hour customer-service window must use
a **message template** pre-approved by Meta. This service lets platform users
author those templates, check them against Meta's rules before submission,
submit them to Meta, keep their review status in sync, and make approved
templates available to the Messaging Service for sending.

## 2. Users and consumers

| Actor | How it uses the service |
|---|---|
| Platform user (business / agent) via the dashboard *(inferred)* | Creates, edits, submits, lists, deletes and syncs templates; uploads header media. Calls arrive through the API gateway. |
| API gateway | Authenticates the user and forwards tenancy headers (`X-Org-Id`, `X-Project-Id`, `X-Waba-Id`). |
| Messaging Service | Reads a template by id (or by name + language) before sending a message. |
| Meta (WhatsApp Business Platform) | Reviews templates; source of truth for approval status. |

## 3. Scope

**In scope:** template CRUD for a project and WABA; draft workflow; local
validation of Meta rules; submission to Meta; pull-sync from Meta including
re-hosting header media; header-media upload to Meta; read APIs for other
services.

**Out of scope (not implemented here):** sending messages; user
authentication and authorization; WABA onboarding and credential storage
(waba-service); media storage (storage-service); webhooks from Meta (status is
pulled by sync, not pushed); template analytics; audit trail; notifications.

## 4. Concepts

- **Tenant scope:** organization → project → WABA (WhatsApp Business Account).
  Every template belongs to one project and one WABA; every read and write is
  limited to the caller's project.
- **Template:** `name` + `language` + `category` + ordered **components**,
  optional **variables** (labelled placeholders).
- **Category:** `MARKETING`, `UTILITY`, `AUTHENTICATION`. Meta may re-classify;
  the previous value is kept in `previousCategory`.
- **Components:** `HEADER` (format `TEXT`, `IMAGE`, `VIDEO`, `DOCUMENT`,
  `LOCATION`, `PRODUCT`), `BODY`, `FOOTER`, `BUTTONS`, `CAROUSEL` (cards with
  their own header/body/buttons), `LIMITED_TIME_OFFER`.
- **Buttons:** `QUICK_REPLY`, `URL`, `PHONE_NUMBER`, `OTP` (`COPY_CODE`,
  `ONE_TAP`, `ZERO_TAP` with supported apps), `COPY_CODE`, `CATALOG`, `MPM`, `SPM`.
- **Placeholders:** positional `{{1}}`, `{{2}}` … or named `{{name}}`, with
  example values.
- **Status lifecycle:**

```
 create (draft=true) ─▶ DRAFT ──update──▶ DRAFT
                          │
         create (draft=false) / submit
                          ▼
                     SUBMITTED ──Meta accepted──▶ status returned by Meta
                          │                         (PENDING / APPROVED / REJECTED …)
                          └──Meta error──▶ FAILED (reason in rejectionReason)

 sync from Meta: PENDING ⇄ APPROVED / REJECTED / PAUSED / DISABLED; missing on Meta ⇒ soft-deleted
```

  Other values in the enum: `NEW_CREATED` (treated by sync as "needs full
  refresh"), `UNKNOWN` (reserved for statuses the service does not model).

## 5. Functional requirements

### FR-1 Create template — `POST /api/v1/templates`
- Input: `template` (`name`, `language`, `category`, `components`),
  optional `variables`, and `draft` (boolean, default `false`).
- Validates bean constraints (name ≤ 150 chars, language ≤ 10 chars,
  component text ≤ 4096 chars, required fields) and then **all Meta rules**
  (FR-10); every violation is returned together (HTTP 422).
- Rejects a duplicate: another non-draft template with the same WABA + name +
  language → HTTP 409.
- Always saves the template first as `DRAFT`, storing the Meta-ready
  snake_case payload.
- If `draft = true`: returns immediately.
- If `draft = false`: submits to Meta (FR-5) in the same request.
- Response includes `id`, `name`, `status`, `category`, `language`,
  `metaTemplateId`, `createdAt`, `updatedAt`.

### FR-2 Get template by id — `GET /api/v1/templates/{templateId}`
- Returns the full template: metadata, components (examples, buttons,
  supported apps, carousel cards and card components), variables,
  `createdAt`, `updatedAt`. 404 if not in the caller's project.
- **Frozen contract** for the Messaging Service; an authenticated twin exists at
  `GET /internal/v1/templates/{templateId}` with an identical body.

### FR-3 Look up template — `GET /api/v1/templates/lookup?name=&language=`
- Finds a template by name + language within the caller's project and WABA.
  Internal twin: `GET /internal/v1/templates/lookup`.

### FR-4 List templates — `GET /api/v1/templates/my-templates`
- Paginated list for the caller's project.
- Filters: `status`, `category`, `search` (case-insensitive substring of name).
- Paging: `page` (default 0), `size` (default 10, max 100).
- Sorting: `sortBy` (entity field, default `createdAt`), `sortDir` (`asc`/`desc`, default `desc`).
- Each item: `id`, `name`, `status`, `category`, `language`,
  `metaTemplateId`, `createdAt`, `updatedAt`.

### FR-5 Submit draft to Meta — `POST /api/v1/templates/{templateId}/submit`
- Only for `DRAFT` templates (else 422 `INVALID_TEMPLATE_STATE`); requires a
  stored payload.
- Marks `SUBMITTED`, fetches the WABA token from waba-service, posts to Meta.
- On success: stores Meta's template id, status and category, and the raw
  Meta response.
- On Meta error: marks `FAILED`, stores the reason, and returns **HTTP 200 with
  envelope `status: "ERROR"`** and the template data, because the template
  exists locally and a retry must not create a duplicate.

### FR-6 Update draft — `PUT /api/v1/templates/{templateId}/draft`
- Only for `DRAFT` templates. Replaces name, category, language, WABA,
  payload, all components and all variables. Duplicate check as FR-1.
- Returns the same shape as FR-1, with the new `updatedAt`.

### FR-7 Delete — `DELETE /api/v1/templates/{templateId}?deleteFromMeta=false`
- Soft-deletes one template in the caller's project (404 if absent).
- `deleteFromMeta=true` also deletes it on Meta **by name** when it has a
  Meta id; Meta failures are logged and do not block the local delete.
  *(Note: Meta's delete-by-name removes all languages of that name.)*
- `DELETE /api/v1/templates` soft-deletes **all** templates in the project
  (local only). Response: `deletedCount`, `projectId`, `templateId`.

### FR-8 Sync from Meta — `POST /api/v1/templates/sync`
- Returns **202** immediately; work runs in the background.
- Fetches every template of the WABA from Meta (pages of 200).
- New on Meta → inserted locally, including components and media.
- Existing → status, category, previous category and rejection reason
  updated when changed.
- Present locally (non-draft) but no longer on Meta → soft-deleted.
- Header media from Meta (templates and carousel cards) is downloaded and
  re-hosted on storage-service; the stored `mediaUrl` points to our storage.
  Media failure does not fail the sync.
- Drafts are never touched by sync. The outcome is visible only via logs and
  by listing templates.

### FR-9 Upload header media — `POST /api/v1/templates/media` (multipart `file`)
- Headers: `X-Waba-Id` selects the WABA whose access token is used;
  `X-App-Id` is the Meta app the upload session is opened on (Meta requires
  the app id; a wrong one is rejected by Meta).
- Uploads a file to Meta through a resumable upload session (retrying once
  from the server-reported offset) and returns `fileName`, `fileSize`,
  `mimeType`, `mediaUrl` (the Meta handle to put in the header example) and
  `sessionId`.
- Limits: 16 MB per file, 64 MB per request (configurable).

### FR-10 Meta rule validation (applied on create)
Collected in one response with `field`, `code` (`META_*`), `message`:
- **Identity:** name `^[a-z0-9_]+$`, ≤ 512 chars; language in the supported
  list (72 locales).
- **Structure:** exactly one BODY (≤ 1024 chars); no duplicate component
  types; TEXT header ≤ 60 chars and no media handle; media header needs a
  handle and no text; LOCATION header has no text; FOOTER ≤ 60 chars and no
  variables; CAROUSEL has 1–10 cards.
- **Placeholders:** not mixed positional/named; start at 1 and sequential;
  header ≤ 1 variable; body must not start or end with a variable; no adjacent
  variables; example values present and matching the count.
- **Buttons:** ≤ 10 total, ≤ 1 phone, ≤ 2 URL; quick replies grouped together;
  label ≤ 25 chars; URL required, ≤ 2000 chars, http(s), at most one variable
  and only at the end; phone in E.164.
- **Category:** AUTHENTICATION — no custom body text, expiry 1–90 min, no
  media header, no custom footer, only OTP/COPY_CODE buttons (supported apps
  required for one-tap/zero-tap). OTP buttons and security recommendation only
  in AUTHENTICATION; commerce buttons not in UTILITY; LIMITED_TIME_OFFER only
  in MARKETING.

## 6. Business rules

- BR-1 Tenancy: a caller can read and change only templates of the project
  in `X-Project-Id`; WABA-specific operations also use `X-Waba-Id`.
- BR-2 Uniqueness: one template per (WABA, name, language) — enforced by a
  unique key; the application pre-check ignores drafts.
- BR-3 Only drafts can be edited or submitted. Submitted/approved templates are immutable here.
- BR-4 Templates are never hard-deleted.
- BR-5 A Meta rejection after a local save is a partial success (HTTP 200,
  `status: "ERROR"`), not an error response.
- BR-6 All timestamps are UTC instants (ISO-8601 with `Z`).

## 7. API conventions

- camelCase JSON; success envelope `{status, message, data}`; error envelope
  `{status, code, errorCode, message, path, timestamp, traceId, fieldErrors}`.
- Machine-readable `errorCode` values: `VALIDATION_FAILED`, `INVALID_REQUEST`,
  `MALFORMED_REQUEST_BODY`, `MISSING_PARAMETER`, `RESOURCE_NOT_FOUND`,
  `ENDPOINT_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNAUTHORIZED`, `PAYLOAD_TOO_LARGE`,
  `DUPLICATE_RESOURCE`, `INVALID_TEMPLATE_STATE`, `TEMPLATE_RULE_VIOLATION`,
  `UPSTREAM_META_API_FAILED`, `UPSTREAM_CREDENTIAL_UNAVAILABLE`,
  `UPSTREAM_MEDIA_UPLOAD_FAILED`, `INTERNAL_ERROR`.
- Correlation: `X-Request-Id` accepted and echoed.
- OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`.

Full endpoint table: `architecture.md` §5.

## 8. Non-functional requirements (as implemented)

| Area | Current behaviour |
|---|---|
| Security | Public API trusts gateway headers; `/internal/**` shared API key (enforced in prod); tokens never stored. |
| Performance | Page size ≤ 100; sync and media work on a bounded pool (15 threads, queue 100, caller-runs back-pressure); no DB connection held during HTTP calls in sync. |
| Reliability | Sync and media re-hosting are best-effort and logged; no retries except one resumable-upload resume; per-upstream timeouts (waba 5 s/10 s, storage 5 s/60 s, Meta 10 s/30 s connect/read). |
| Observability | Actuator health/liveness/readiness, metrics (+ Prometheus in prod); trace/org/project in every log line. |
| Data | MySQL, soft delete, UTC timestamps; schema managed by Hibernate from entities. |
| Deployability | Profiles `dev`/`prod`; Eureka registration; graceful shutdown. |

## 9. Open questions *(for product owner)*

- Should sync report its result (job id / status endpoint or callback) instead of only logs?
- Should update-draft and submit re-run Meta rule validation (today only create does)?
- Should Meta status changes arrive by webhook rather than manual sync?
- Is an audit trail required? (`TemplateAuditEventType` exists but is unused.)
- Should uploaded media be recorded (`whatsapp_template_media_uploads` is unused)?
