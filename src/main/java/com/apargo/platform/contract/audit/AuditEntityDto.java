package com.apargo.platform.contract.audit;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The single record an event is about. Omitted for events that cover many
 * records (a bulk delete, a sync).
 *
 * @param type required, upper snake case, e.g. {@code TEMPLATE}
 * @param id   required; may be {@code null} only on a {@code FAILURE} where
 *             the record was never created
 * @param name the record's name at the time of the action
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEntityDto(String type, String id, String name) {
}
