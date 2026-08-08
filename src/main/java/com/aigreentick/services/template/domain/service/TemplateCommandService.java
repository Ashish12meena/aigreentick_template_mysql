package com.aigreentick.services.template.domain.service;

import java.util.List;
import java.util.Set;

import com.aigreentick.services.template.domain.model.WhatsappTemplate;

/**
 * Write operations on WhatsApp templates.
 */
public interface TemplateCommandService {

    WhatsappTemplate save(WhatsappTemplate template);

    List<WhatsappTemplate> saveAll(List<WhatsappTemplate> templates);

    // ── Status transitions ──

    void markAsSubmitted(WhatsappTemplate template);

    void markAsSucceeded(WhatsappTemplate template, String metaTemplateId, String status, String metaResponse);

    void markAsFailed(WhatsappTemplate template, String errorMessage, String metaResponse);

    void markAsNewCreated(WhatsappTemplate template, String metaTemplateId, String status, String metaResponse);
    // ── Deletes ──

    int softDeleteById(Long id, Long projectId);

    int softDeleteAllByProject(Long projectId);

    int softDeleteStaleByMetaIds(Set<String> metaIds, Long projectId);

    // ── Validation ──

    void ensureNoDuplicate(String wabaId, String name, String language, Long excludeTemplateId);

}