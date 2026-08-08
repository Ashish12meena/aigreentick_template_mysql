/**
 * API boundary — HTTP controllers, request/response DTOs, and the
 * mappers between them and the application layer.
 *
 * Rules (see RULES.md):
 *  - This is the ONLY layer allowed to know HTTP exists (status codes,
 *    headers, {@code @RequestBody}, etc).
 *  - Controllers depend on {@code application.port.in} interfaces only —
 *    never on a concrete {@code *UseCaseImpl} class, and never on
 *    {@code domain} entities or repositories directly.
 *  - {@code api.mapper} is the only place that converts between
 *    {@code api.dto} and whatever the application layer expects.
 */
package com.aigreentick.services.template.api;
