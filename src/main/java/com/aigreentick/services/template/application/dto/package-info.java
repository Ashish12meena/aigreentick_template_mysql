/**
 * This layer's own request/response-shaped models — its boundary types.
 *
 * Sub-packages:
 *  - {@code command} — inputs to use cases (e.g. {@code CreateTemplateCommand}),
 *                       built by {@code api.mapper} from {@code api.request}.
 *  - {@code result}  — outputs from use cases (e.g. {@code TemplateResult}),
 *                       converted by {@code api.mapper} into {@code api.response}.
 *  - {@code client}  — shapes exchanged with outbound ports.
 *
 * Note: the commands currently carry {@code api.request} DTOs (e.g.
 * {@code BaseTemplateRequestDto}) as their payload. See the layering section
 * of {@code src/main/resources/docs/rules.md}.
 */
package com.aigreentick.services.template.application.dto;
