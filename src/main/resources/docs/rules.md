# RULES.md — WhatsApp Template Service

These are not style preferences; each exists because breaking it caused a
specific problem in this codebase.

## Layering

- `domain` depends on nothing else in the project.
- `application` depends only on `domain`.
- `infrastructure` implements `application/port/out` — never the other way round.
- `api` never touches entities or repositories directly — always through a
  `port.in` use case.

## Packages

- No empty packages. If a folder exists, it has a real class in it — not
  "for later." (18 were removed.)
- One interface, one place. There must never be a second copy of a port in a
  parallel package — `application/port/X` and `application/port/out/X` had
  already drifted apart, and the shadowed legacy `@Service` classes under
  `application/usecase` were still being component-scanned.
- No speculative scaffolding. `TemplateAuditPort` and its two event DTOs were
  declared, never implemented and never called; they were removed rather than
  left as a promise.

## Naming

- One field, one name, everywhere — entity, DTO, repository method, query
  param. `wabaId` means Meta's WABA id in every layer; waba-service's internal
  row id is `wabaAccountId` and is never used as a substitute.
- A name must describe the thing. The credential DTO's `appId` field actually
  held a Phone Number ID, which pointed readers at the wrong concept.

## Configuration

- One service = one database schema. The dev profile pointed at
  `apargo_wa_messaging` — another service's schema — with `ddl-auto: validate`.
- No hardcoded configuration. Everything binds through a
  `@ConfigurationProperties` class registered in
  `PropertiesRegistrationConfig`. No stray `@Value`.
- No secret has a default in the `prod` profile.
- `ddl-auto` is never `update` in production.

## API

- Every public endpoint is versioned.
- Every route lives in `ApiPaths`.
- `/internal/**` is authenticated.
- One success envelope (`ResponseMessage`) and one error envelope
  (`ErrorResponse`).
- Errors carry a stable `errorCode`. Callers must never have to branch on
  English prose.
- Health checks come from Actuator, not a hand-rolled endpoint that reports
  `UP` because it was reachable.

## Persistence

- Every table gets `created_at`, `updated_at`, `deleted_at` (soft delete).
  Never a hard `DELETE` on `whatsapp_templates`.

## Frozen contract

`GET /api/v1/templates/{templateId}` — consumed by the Messaging Service.
Path, `X-Project-Id` header and snake_case `{status, message, data}` envelope
are fixed. See `ARCHITECTURE.md`.

All request and response bodies on this service are **snake_case**. That is a
live contract, not a preference.
