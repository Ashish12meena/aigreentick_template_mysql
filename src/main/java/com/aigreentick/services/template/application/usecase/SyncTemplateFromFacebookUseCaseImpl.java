package com.aigreentick.services.template.application.usecase;

import com.aigreentick.services.template.infrastructure.config.FacebookJacksonConfig;
import com.aigreentick.services.template.infrastructure.config.MediaSyncThreadPoolConfig;
import com.aigreentick.services.template.application.port.in.SyncTemplateFromFacebookUseCase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.api.request.SyncTemplateRequest;
import com.aigreentick.services.template.api.response.TemplateSyncStats;
import com.aigreentick.services.template.application.dto.client.AccessTokenIdentifier;
import com.aigreentick.services.template.application.dto.client.FacebookApiResponse;
import com.aigreentick.services.template.application.dto.TenantScope;
import com.aigreentick.services.template.application.mapper.TemplateSyncMapper;
import com.aigreentick.services.template.application.port.out.FacebookTemplateSyncPort;
import com.aigreentick.services.template.application.port.out.WabaCredentialPort;
import com.aigreentick.services.template.application.service.MediaSyncService;
import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.service.TemplateCommandService;
import com.aigreentick.services.template.domain.service.TemplateQueryService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Syncs WhatsApp templates from Facebook into the local database.
 *
 * Phase 1 — Fetch : pure HTTP, no DB connection
 * Phase 2 — Categorize : readOnly transaction, connection released before Phase
 * 3
 * Phase 3 — Media : pure HTTP, no DB connection
 * Phase 4 — Persist : short transaction, connection released on commit
 */
@Service
@Slf4j
public class SyncTemplateFromFacebookUseCaseImpl implements SyncTemplateFromFacebookUseCase {

    private final WabaCredentialPort accountClient;
    private final FacebookTemplateSyncPort ftsp;
    /**
     * Meta serializes snake_case, so this is deliberately NOT the
     * auto-configured context mapper. See {@link FacebookJacksonConfig} —
     * binding Meta payloads with a camelCase mapper drops
     * {@code example.header_handle} silently and sync completes with every
     * template missing its media.
     */
    private final ObjectMapper facebookObjectMapper;
    private final TemplateQueryService queryService;
    private final TemplateCommandService commandService;
    private final TemplateSyncMapper syncMapper;
    private final MediaSyncService mediaSyncService;
    private final Executor mediaSyncExecutor;

    public SyncTemplateFromFacebookUseCaseImpl(
            WabaCredentialPort accountClient,
            FacebookTemplateSyncPort ftsp,
            @Qualifier(FacebookJacksonConfig.FACEBOOK_OBJECT_MAPPER) ObjectMapper facebookObjectMapper,
            TemplateQueryService queryService,
            TemplateCommandService commandService,
            TemplateSyncMapper syncMapper,
            MediaSyncService mediaSyncService,
            @Qualifier(MediaSyncThreadPoolConfig.MEDIA_SYNC_EXECUTOR) Executor mediaSyncExecutor) {

        this.accountClient = accountClient;
        this.ftsp = ftsp;
        this.facebookObjectMapper = facebookObjectMapper;
        this.queryService = queryService;
        this.commandService = commandService;
        this.syncMapper = syncMapper;
        this.mediaSyncService = mediaSyncService;
        this.mediaSyncExecutor = mediaSyncExecutor;
    }

    private record SyncCategorizationResult(
            List<WhatsappTemplate> toInsert,
            List<WhatsappTemplate> toUpdate,
            Set<String> staleMetaIds,
            List<WhatsappTemplate> allNeedingMedia,
            int skippedCount) {
    }

    public TemplateSyncStats execute(Long projectId, Long organizationId, String wabaId) {
        log.info("Sync requested — firing async background job. projectId={} wabaId={}",
                projectId, wabaId);

        CompletableFuture.runAsync(
                () -> runSync(projectId, organizationId, wabaId),
                mediaSyncExecutor).exceptionally(ex -> {
                    log.error("Background sync failed. projectId={} wabaId={}", projectId, wabaId, ex);
                    return null;
                });

        return TemplateSyncStats.started();
    }

    // No @Transactional — each phase acquires and releases its own DB connection
    private void runSync(Long projectId, Long organizationId, String wabaId) {
        log.info("[SYNC-START] projectId={} wabaId={}", projectId, wabaId);
        long totalStart = System.currentTimeMillis();

        try {
            // Phase 1: pure HTTP, no DB connection
            AccessTokenIdentifier credentials = accountClient
                    .getWhatsappAccountWabaAccessToken(wabaId, new TenantScope(organizationId, projectId));

            List<SyncTemplateRequest> facebookTemplates = fetchAllTemplatesPaginated(
                    wabaId, credentials.getAccessToken());

            log.info("[SYNC] Phase 1 done — fetched {} templates from Facebook for projectId={}",
                    facebookTemplates.size(), projectId);

            // Phase 2: readOnly transaction — connection released when method returns
            SyncCategorizationResult categorized = categorizeTemplates(
                    projectId, organizationId, wabaId, facebookTemplates);

            log.info("[SYNC] Phase 2 done — toInsert={} toUpdate={} stale={} skipped={}",
                    categorized.toInsert().size(),
                    categorized.toUpdate().size(),
                    categorized.staleMetaIds().size(),
                    categorized.skippedCount());

            // Phase 3: pure HTTP, no DB connection
            if (!categorized.allNeedingMedia().isEmpty()) {
                log.info("[SYNC] Phase 3 starting — resolving media for {} template(s)",
                        categorized.allNeedingMedia().size());

                long mediaStart = System.currentTimeMillis();
                mediaSyncService.resolveMediaForTemplates(
                        categorized.allNeedingMedia(), organizationId, projectId, wabaId);

                log.info("[SYNC] Phase 3 done — media resolved in {}ms",
                        System.currentTimeMillis() - mediaStart);
            } else {
                log.debug("[SYNC] Phase 3 skipped — no media to resolve");
            }

            // Phase 4: short transaction — connection acquired after Phase 3 completes
            TemplateSyncStats stats = persistChanges(projectId, wabaId, categorized);

            log.info("[SYNC-DONE] projectId={} wabaId={} duration={}ms — inserted={} updated={} deleted={}",
                    projectId, wabaId, System.currentTimeMillis() - totalStart,
                    stats.inserted(), stats.updated(), stats.deleted());

        } catch (Exception ex) {
            log.error("[SYNC-ERROR] Background sync failed after {}ms. projectId={} wabaId={}",
                    System.currentTimeMillis() - totalStart, projectId, wabaId, ex);
        }
    }

    private List<SyncTemplateRequest> fetchAllTemplatesPaginated(
            String wabaId, String accessToken) {

        List<SyncTemplateRequest> allTemplates = new ArrayList<>();
        Optional<String> afterCursor = Optional.empty();
        int pageNumber = 0;

        do {
            log.info("[SYNC] Fetching FB page={} wabaId={}", pageNumber, wabaId);

            FacebookApiResponse<JsonNode> response = ftsp.getAllTemplates(
                    wabaId, accessToken,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(200),
                    afterCursor);

            if (!response.isSuccess() || response.getData() == null) {
                throw new IllegalStateException(
                        "Failed to fetch templates from Facebook page=" + pageNumber
                                + ": " + response.getErrorMessage());
            }

            List<SyncTemplateRequest> pageTemplates = parseFacebookResponse(response.getData());
            allTemplates.addAll(pageTemplates);

            afterCursor = extractNextCursor(response.getData());
            pageNumber++;

            log.info("[SYNC] Page={} fetched {} templates, hasNext={}",
                    pageNumber, pageTemplates.size(), afterCursor.isPresent());

        } while (afterCursor.isPresent());

        log.info("[SYNC] Pagination done — {} templates across {} page(s)",
                allTemplates.size(), pageNumber);

        return allTemplates;
    }

    // readOnly = true: no HTTP inside, connection released the moment this method
    // returns
    @Transactional(readOnly = true)
    protected SyncCategorizationResult categorizeTemplates(
            Long projectId, Long organizationId, String wabaId,
            List<SyncTemplateRequest> facebookTemplates) {

        Set<String> facebookMetaIds = extractMetaIds(facebookTemplates);
        Set<String> existingMetaIds = queryService.findSyncedMetaIds(projectId, wabaId);

        log.info("[SYNC] DB has {} existing non-draft templates for projectId={}",
                existingMetaIds.size(), projectId);

        Set<String> newMetaIds = new HashSet<>(facebookMetaIds);
        newMetaIds.removeAll(existingMetaIds);

        Set<String> staleMetaIds = new HashSet<>(existingMetaIds);
        staleMetaIds.removeAll(facebookMetaIds);

        log.info("[SYNC] Categorized — new: {}, stale: {}, potential updates: {}",
                newMetaIds.size(), staleMetaIds.size(),
                facebookMetaIds.size() - newMetaIds.size());

        Map<String, WhatsappTemplate> existingByMetaId = queryService
                .findAllByMetaIds(existingMetaIds, projectId)
                .stream()
                .collect(Collectors.toMap(
                        WhatsappTemplate::getMetaTemplateId, Function.identity()));

        List<WhatsappTemplate> toInsert = new ArrayList<>();
        List<WhatsappTemplate> toUpdate = new ArrayList<>();
        List<WhatsappTemplate> needsMediaResolution = new ArrayList<>();
        int skippedCount = 0;

        for (SyncTemplateRequest fbTemplate : facebookTemplates) {
            String metaId = fbTemplate.getMetaTemplateId();
            if (metaId == null || metaId.isBlank()) {
                log.warn("[SYNC] Facebook template has no metaTemplateId, skipping: {}",
                        fbTemplate.getName());
                continue;
            }

            if (newMetaIds.contains(metaId)) {
                toInsert.add(syncMapper.fromFacebookTemplate(
                        fbTemplate, projectId, organizationId, wabaId));

            } else if (existingMetaIds.contains(metaId)) {
                WhatsappTemplate existing = existingByMetaId.get(metaId);
                if (existing == null) {
                    log.warn("[SYNC] Template metaId={} not in prefetched map, skipping", metaId);
                    continue;
                }

                if (hasMetadataChanged(existing, fbTemplate)) {
                    boolean wasNewCreated = existing.getStatus() == TemplateStatus.NEW_CREATED;
                    applyMetadataUpdate(existing, fbTemplate);
                    toUpdate.add(existing);

                    if (wasNewCreated) {
                        needsMediaResolution.add(existing);
                        log.info("[SYNC] Template metaId={} promoting from NEW_CREATED, queued for media", metaId);
                    }
                } else {
                    skippedCount++;
                }
            }
        }

        List<WhatsappTemplate> allNeedingMedia = new ArrayList<>(toInsert);
        allNeedingMedia.addAll(needsMediaResolution);

        return new SyncCategorizationResult(
                toInsert, toUpdate, staleMetaIds, allNeedingMedia, skippedCount);
    }

    @Transactional
    protected TemplateSyncStats persistChanges(
            Long projectId, String wabaId, SyncCategorizationResult categorized) {

        if (!categorized.toInsert().isEmpty()) {
            commandService.saveAll(categorized.toInsert());
            log.info("[SYNC] Inserted {} new templates for projectId={}",
                    categorized.toInsert().size(), projectId);
        }

        if (!categorized.toUpdate().isEmpty()) {
            commandService.saveAll(categorized.toUpdate());
            log.info("[SYNC] Updated {} templates for projectId={}",
                    categorized.toUpdate().size(), projectId);
        }

        int deletedCount = 0;
        if (!categorized.staleMetaIds().isEmpty()) {
            deletedCount = commandService.softDeleteStaleByMetaIds(
                    categorized.staleMetaIds(), projectId);
            log.info("[SYNC] Soft-deleted {} stale templates for projectId={}",
                    deletedCount, projectId);
        }

        log.info("[SYNC] Persist complete — inserted={} updated={} skipped={} deleted={} mediaQueued={}",
                categorized.toInsert().size(),
                categorized.toUpdate().size(),
                categorized.skippedCount(),
                deletedCount,
                categorized.allNeedingMedia().size());

        return new TemplateSyncStats(
                categorized.toInsert().size(),
                categorized.toUpdate().size(),
                deletedCount);
    }

    private Optional<String> extractNextCursor(JsonNode responseNode) {
        JsonNode paging = responseNode.path("paging");
        if (paging.isMissingNode())
            return Optional.empty();

        JsonNode next = paging.path("next");
        if (next.isMissingNode() || next.asText("").isBlank())
            return Optional.empty();

        JsonNode after = paging.path("cursors").path("after");
        if (after.isMissingNode() || after.asText("").isBlank())
            return Optional.empty();

        return Optional.of(after.asText());
    }

    private Set<String> extractMetaIds(List<SyncTemplateRequest> templates) {
        return templates.stream()
                .map(SyncTemplateRequest::getMetaTemplateId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
    }

    private boolean hasMetadataChanged(WhatsappTemplate existing, SyncTemplateRequest fbTemplate) {
        if (existing.getStatus() == TemplateStatus.NEW_CREATED)
            return true;

        if (fbTemplate.getStatus() != null
                && fbTemplate.getStatus() != existing.getStatus())
            return true;

        TemplateCategory fbCategory = parseCategory(fbTemplate.getCategory());
        if (fbCategory != null && fbCategory != existing.getCategory())
            return true;

        TemplateCategory fbPrevCategory = parseCategory(fbTemplate.getPreviousCategory());
        if (fbPrevCategory != null
                && fbPrevCategory != existing.getPreviousCategory())
            return true;

        return !equalsNullSafe(existing.getRejectionReason(), fbTemplate.getRejectionReason());
    }

    private void applyMetadataUpdate(WhatsappTemplate existing, SyncTemplateRequest fbTemplate) {
        if (fbTemplate.getStatus() != null)
            existing.setStatus(fbTemplate.getStatus());

        TemplateCategory fbCategory = parseCategory(fbTemplate.getCategory());
        if (fbCategory != null)
            existing.setCategory(fbCategory);

        TemplateCategory fbPrevCategory = parseCategory(fbTemplate.getPreviousCategory());
        if (fbPrevCategory != null)
            existing.setPreviousCategory(fbPrevCategory);

        existing.setRejectionReason(fbTemplate.getRejectionReason());
    }

    private TemplateCategory parseCategory(String category) {
        if (category == null || category.isBlank())
            return null;
        try {
            return TemplateCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[SYNC] Invalid category from Facebook: {}", category);
            return null;
        }
    }

    private boolean equalsNullSafe(String a, String b) {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        return a.equals(b);
    }

    private List<SyncTemplateRequest> parseFacebookResponse(JsonNode responseNode) {
        List<SyncTemplateRequest> parsed = new ArrayList<>();
        int skipped = 0;

        // Meta wraps results: { "data": [ ... ], "paging": { ... } }.
        // Iterating responseNode directly walks the wrapper's FIELD VALUES —
        // the `data` array and the `paging` object — not the templates.
        JsonNode dataNode = responseNode.path("data");
        if (!dataNode.isArray()) {
            log.warn("[SYNC] Expected 'data' array in Meta response, got {}",
                    dataNode.getNodeType());
            return parsed;
        }

        for (JsonNode templateNode : dataNode) {
            try {
                parsed.add(facebookObjectMapper.treeToValue(templateNode, SyncTemplateRequest.class));
            } catch (Exception e) {
                skipped++;
                log.warn("[SYNC] Skipping unparseable template metaId={} reason={}",
                        templateNode.path("id").asText("<unknown>"), e.getMessage());
            }
        }

        if (skipped > 0) {
            log.warn("[SYNC] Skipped {} of {} templates in this page due to parse failures",
                    skipped, dataNode.size());
        }
        return parsed;
    }
}