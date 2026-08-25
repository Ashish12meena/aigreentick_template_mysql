/**
 * This layer's own request/response-shaped models — its boundary types.
 *
 * Sub-packages:
 *  - {@code command} — inputs to use cases (e.g. {@code CreateTemplateCommand}),
 *                       built by {@code api.mapper} from {@code api.dto.request}.
 *  - {@code result}  — outputs from use cases (e.g. {@code TemplateResult}),
 *                       converted by {@code api.mapper} into {@code api.dto.response}.
 *  (An {@code audit} sub-package previously listed here declared a
 *  TemplateAuditPort and two event DTOs. Nothing implemented the port and
 *  nothing called it - a closed loop of speculative scaffolding, which
 *  docs/rules.md explicitly forbids. Removed rather than left as a promise.)
 *
 * Rule: nothing in this package imports from {@code api.dto} — see
 * RULES.md, "application never imports from api."
 */
package com.aigreentick.services.template.application.dto;
