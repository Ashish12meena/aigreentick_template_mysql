# RULES.md — WhatsApp Template Service

- `domain` depends on nothing else in the project. `application` depends only on `domain`. `infrastructure` implements `application/port` — never the other way round. `api` never touches entities or repositories directly — always goes through a use case.
- No empty packages. If a folder exists, it has a real class in it — not "for later."
- One field, one name, everywhere — entity, DTO, repository method, query param (e.g. `wabaId` everywhere, not `wabaId` in one place and `wabaAccountId` in another for the same concept).
- Every table gets `created_at`, `updated_at`, `deleted_at` (soft delete). Never a hard `DELETE` on `whatsapp_templates`.

