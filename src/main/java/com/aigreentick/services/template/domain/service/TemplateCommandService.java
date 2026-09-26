package com.aigreentick.services.template.domain.service;

import java.util.List;
import java.util.Set;

import com.aigreentick.services.template.domain.model.SystemTemplate;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;

/**
 * Write operations on WhatsApp templates.
 */
public interface TemplateCommandService {

    WhatsappTemplate save(WhatsappTemplate template);

    List<WhatsappTemplate> saveAll(List<WhatsappTemplate> templates);

    // ── Status transitions ──
    //
    // Each runs in its own short transaction and re-reads the row, so they are
    // safe to call from code that holds no transaction (the Meta submission
    // flow deliberately holds none while it waits on Meta).

    /**
     * DRAFT -> SUBMITTED, only if the row is still a DRAFT. Throws
     * {@code InvalidTemplateStateException} otherwise (e.g. a concurrent
     * submit already moved it). Can raise a unique-key violation if another
     * live template already uses the name.
     */
    void markAsSubmitted(WhatsappTemplate template);

    /**
     * SUBMITTED -> Meta's status (PENDING / APPROVED / ...). Never throws on
     * an unrecognised Meta status or category: an unknown status is stored as
     * UNKNOWN with the raw value in metaStatusRaw. The passed entity is
     * updated to match. Returns false (and changes nothing) if the row is no
     * longer SUBMITTED.
     *
     * @param metaResponse valid JSON or null (the column is JSON)
     */
    boolean markAsAcceptedByMeta(WhatsappTemplate template, String metaTemplateId,
            String metaStatus, String metaCategory, String metaResponse);

    /**
     * SUBMITTED -> FAILED. FAILED is not live, so the name becomes reusable
     * while this row (with Meta's error) stays as history. Returns false if
     * the row is no longer SUBMITTED.
     *
     * @param metaResponse valid JSON or null (the column is JSON)
     */
    boolean markAsFailed(WhatsappTemplate template, String errorMessage, String metaResponse);

    void flush();

    // ── Deletes ──

    int softDeleteById(Long id, Long projectId);

    int softDeleteAllByProject(Long projectId);

    int softDeleteStaleByMetaIds(Set<String> metaIds, Long projectId);

    // ── Validation ──

    void ensureNoDuplicate(String wabaId, String name, String language, Long excludeTemplateId);

    // ── Template Library ──

    SystemTemplate saveSystemTemplate(SystemTemplate systemTemplate);

    /** 409 {@code SYSTEM_TEMPLATE_ALREADY_EXISTS} if another library entry uses this name + language. */
    void ensureNoDuplicateSystemTemplate(String name, String language, Long excludeId);
}
