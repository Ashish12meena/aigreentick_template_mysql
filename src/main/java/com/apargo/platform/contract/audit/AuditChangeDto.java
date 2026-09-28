package com.apargo.platform.contract.audit;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One changed field.
 *
 * <p>Both values are always serialized, including {@code null}: a field that
 * went from nothing to something (or back) is itself the change. Values are
 * scalars (string, number, boolean, enum name); never nested bodies, secrets
 * or tokens.
 *
 * @param field dotted path, e.g. {@code status} or {@code header.format}
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AuditChangeDto(String field, Object oldValue, Object newValue) {
}
