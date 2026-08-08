/**
 * This layer's own request/response-shaped models — its boundary types.
 *
 * Sub-packages:
 *  - {@code command} — inputs to use cases (e.g. {@code CreateTemplateCommand}),
 *                       built by {@code api.mapper} from {@code api.dto.request}.
 *  - {@code result}  — outputs from use cases (e.g. {@code TemplateResult}),
 *                       converted by {@code api.mapper} into {@code api.dto.response}.
 *  - {@code audit}   — events published outward via {@code port.out.TemplateAuditPort}.
 *
 * Rule: nothing in this package imports from {@code api.dto} — see
 * RULES.md, "application never imports from api."
 */
package com.aigreentick.services.template.application.dto;
