package com.aigreentick.services.template.application.dto;

import com.aigreentick.services.template.domain.model.WhatsappTemplate;

/**
 * The tenancy pair that scopes every outbound credential lookup.
 *
 * Deliberately a record and not two loose Long parameters: organizationId and
 * projectId are the same type, so a swapped call site compiles cleanly and
 * fails silently — authorising against the wrong tenant. The record makes that
 * mistake impossible.
 */
public record TenantScope(Long organizationId, Long projectId) {

    public TenantScope {
        if (organizationId == null || projectId == null) {
            throw new IllegalArgumentException("organizationId and projectId are both required");
        }
    }

    /** Templates already carry their owning tenant, so derive rather than re-thread. */
    public static TenantScope of(WhatsappTemplate template) {
        return new TenantScope(template.getOrganizationId(), template.getProjectId());
    }
}