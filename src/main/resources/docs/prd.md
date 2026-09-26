# Template Service — Product Requirements Specification

Describes what the service does **today**, derived from the implementation.
Items marked *(inferred)* are reasoned from code, not from a written business
spec; confirm them with the product owner. Last reviewed: 2026-09-24.

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

**In scope:** template CRUD for a project and WABA; a system-wide Template
Library of predefined templates; draft workflow; local
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
- Requires `X-Idempotency-Key`; a repeat with the same key returns the first
  response (HTTP 200) instead of creating again.
- Validates bean constraints (name ≤ 150 chars, language ≤ 10 chars,
  component text ≤ 4096 chars, required fields) and then **all Meta rules**
  (FR-10); every violation is returned together (HTTP 422 `VALIDATION_FAILED`).
- Rejects a duplicate: another non-draft template with the same WABA + name +
  language → HTTP 409 `TEMPLATE_ALREADY_EXISTS`.
- Always saves the template first as `DRAFT`, storing the Meta-ready
  snake_case payload.
- If `draft = true`: returns immediately.
- If `draft = false`: submits to Meta (FR-5) in the same request.
- Response: **201 Created** with `Location: /api/v1/templates/{id}`; `data`
  includes `id`, `name`, `status`, `category`, `language`, `metaTemplateId`,
  `createdAt`, `updatedAt` (and `errorMessage`/`errorPayload` if Meta rejected it).

### FR-2 Get template by id — `GET /api/v1/templates/{templateId}`
- Returns the full template: metadata, components (examples, buttons,
  supported apps, carousel cards and card components), variables,
  `createdAt`, `updatedAt`. 404 `TEMPLATE_NOT_FOUND` if not in the caller's project.
- Read by the Messaging Service on the send path; an authenticated twin exists at
  `GET /internal/v1/templates/{templateId}` with an identical body.

### FR-3 Look up template — `GET /api/v1/templates/lookup?name=&language=`
- Finds a template by name + language within the caller's project and WABA.
  Internal twin: `GET /internal/v1/templates/lookup`.

### FR-4 List templates — `GET /api/v1/templates/my-templates`
- Page-based list for the caller's project; `data = {items, pagination}`.
- Filters: `status`, `category`, `search` (case-insensitive substring of name).
- Paging: `page` (from 0, default 0), `size` (1–100, default 20).
- Sorting: `sort` ∈ `createdAt` (default), `updatedAt`, `name`, `status`,
  `category`, `language`; `order` `asc`/`desc` (default `desc`). `id` is
  always the tie-breaker, so paging is stable.
- Invalid `page`, `size`, `sort`, `order` or filter value → 422
  `VALIDATION_FAILED`. A page past the end → 200 with `items: []`.
- Each item: `id`, `name`, `status`, `category`, `language`,
  `metaTemplateId`, `createdAt`, `updatedAt`.

### FR-5 Submit draft to Meta — `POST /api/v1/templates/{templateId}/submit`
- Requires `X-Idempotency-Key` (a repeat replays the first response).
- Only for `DRAFT` templates (else 409 `TEMPLATE_INVALID_STATE`); requires a
  stored payload.
- Marks `SUBMITTED`, fetches the WABA token from waba-service, posts to Meta.
- On success: stores Meta's template id, status and category, and the raw
  Meta response.
- On Meta error: marks `FAILED`, stores the reason, and returns **HTTP 200**
  (`success: true`) with `data.status = FAILED` and `data.errorMessage`, because
  the call did change the template and a retry must not create a duplicate.

### FR-6 Update draft — `PUT /api/v1/templates/{templateId}/draft`
- Only for `DRAFT` templates. Replaces name, category, language, WABA,
  payload, all components and all variables. Duplicate check as FR-1.
- Returns the same shape as FR-1, with the new `updatedAt`.

### FR-7 Delete — `DELETE /api/v1/templates/{templateId}?deleteFromMeta=false`
- Soft-deletes one template in the caller's project: **204 No Content**
  (404 `TEMPLATE_NOT_FOUND` if absent).
- `deleteFromMeta=true` also deletes it on Meta **by name** when it has a
  Meta id; Meta failures are logged and do not block the local delete.
  *(Note: Meta's delete-by-name removes all languages of that name.)*
- `DELETE /api/v1/templates` soft-deletes **all** templates in the project
  (local only). Response: 200 with `data.deletedCount`.

### FR-8 Sync from Meta — `POST /api/v1/templates/sync`
- Returns **202** immediately with `data = {jobId, statusUrl}`: `jobId` is the
  request id (it tags every log line of the background job) and `statusUrl`
  is the template list.
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

### FR-11 Template Library — browse — `GET /api/v1/template-library`, `GET /api/v1/template-library/{systemTemplateId}`
- The Template Library holds predefined, system-level templates (not owned
  by any organization or project) that users can start from.
- List: active entries only; filters `category`, `language`, `search`
  (name or description); paging and sorting as FR-4 (`sort` ∈ `createdAt`,
  `updatedAt`, `name`, `category`, `language`). Each item: `id`, `name`,
  `language`, `category`, `description`, `sampleMediaUrl`, `createdAt`,
  `updatedAt`.
- Detail: `id`, `description`, `sampleMediaUrl`, `active`, `template`,
  `variables`, `createdAt`, `updatedAt`.
- `sampleMediaUrl` is a public URL on our own storage used only to preview a
  media-header template in the library UI. It is not a Meta handle and is
  never sent to Meta. `template` and `variables` have **the same shape
  as the FR-1 create request**. Missing or inactive → 404
  `SYSTEM_TEMPLATE_NOT_FOUND`.
- `X-Org-Id` / `X-Project-Id` are required but do not filter the library.
- **Using a library template:** the client pre-fills its create form from
  `template` + `variables`, lets the user change the name and values, and
  calls FR-1. There is no separate "use" endpoint, so validation, duplicate
  checks, drafts and Meta submission are unchanged. A media header still
  needs the user's own upload (FR-9): Meta media handles are app-specific.

### FR-12 Template Library — maintain — `POST` / `PUT /internal/v1/template-library[/{systemTemplateId}]`
- Internal only (`X-Internal-Api-Key`); never exposed through the gateway.
- Body: `description`, optional `sampleMediaUrl` (http(s), ≤ 500 chars;
  upload the sample file to storage-service first), `template`, `variables`
  (FR-1 shapes) and `active` (default `true`). Validated with FR-10 rules; name + language must be
  unique in the library (409 `SYSTEM_TEMPLATE_ALREADY_EXISTS`).
- Create requires `X-Idempotency-Key`; returns 201 with `Location` at the
  public read path. Update is a full replace (also of `active`) and returns 200.
- Entries are never deleted; `active: false` hides one. Templates users
  already created from an entry are independent and never change.

## 6. Business rules

- BR-1 Tenancy: a caller can read and change only templates of the project
  in `X-Project-Id`; WABA-specific operations also use `X-Waba-Id`.
- BR-2 Uniqueness: one template per (WABA, name, language) — enforced by a
  unique key; the application pre-check ignores drafts.
- BR-3 Only drafts can be edited or submitted. Submitted/approved templates are immutable here.
- BR-4 Templates are never hard-deleted.
- BR-5 A Meta rejection after a local save is a successful call (2xx,
  `success: true`) whose `data` carries `status: FAILED` and `errorMessage`;
  it is never an error response.
- BR-6 All timestamps are UTC instants (ISO-8601 with `Z`).
- BR-7 Template Library entries are global (no tenant) and read-only for
  users; a user's template made from one is a copy, not a reference.

## 7. API conventions

The service follows the company **API Standard** (headers, request format,
response wrapper, status codes, pagination, error format).

- camelCase JSON; enums `UPPER_SNAKE_CASE`; times ISO-8601 UTC.
- Headers: `X-Org-Id` and `X-Project-Id` required on every business endpoint;
  `X-User-Id` optional; `X-Request-Id` optional (generated if absent, always
  echoed, the only tracking header); `X-Idempotency-Key` required on create
  and submit. Service-specific: `X-Waba-Id`, `X-App-Id`.
- Every JSON response (success and error) is
  `{success, status, code, message, data, errors, meta}`; `status` always
  equals the HTTP status; success has `code: "SUCCESS"`; lists are
  `data = {items, pagination}`. `204` responses have no body.
- Error `code` values: `BAD_REQUEST`, `UNAUTHENTICATED`, `NOT_FOUND`,
  `CONFLICT`, `VALIDATION_FAILED`, `INTERNAL_ERROR`, `DEPENDENCY_FAILURE`,
  `TIMEOUT`, `METHOD_NOT_ALLOWED`, `PAYLOAD_TOO_LARGE`,
  `UNSUPPORTED_MEDIA_TYPE`, `TEMPLATE_NOT_FOUND`, `TEMPLATE_ALREADY_EXISTS`,
  `TEMPLATE_INVALID_STATE`, `SYSTEM_TEMPLATE_NOT_FOUND`,
  `SYSTEM_TEMPLATE_ALREADY_EXISTS`, `WABA_CREDENTIALS_UNAVAILABLE`,
  `MEDIA_UPLOAD_FAILED`, `IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_KEY_IN_PROGRESS`,
  `IDEMPOTENCY_KEY_REUSED` (the idempotency codes are shared with storage-service).
- Field codes in `errors[].code`: the standard's `REQUIRED`, `INVALID_FORMAT`,
  `INVALID_VALUE`, `TOO_LONG`, `OUT_OF_RANGE`; Meta rules keep `META_*`.
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
