package com.aigreentick.services.template.infrastructure.idempotency;

import com.aigreentick.services.template.infrastructure.config.properties.IdempotencyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence for idempotency keys. Every method runs in its own short
 * transaction ({@code REQUIRES_NEW}), independent of whatever the endpoint
 * itself does, so a rolled-back business transaction can't lose the key's
 * state and no connection is held while the endpoint calls Meta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyStore {

    private final IdempotencyRecordRepository repository;
    private final IdempotencyProperties properties;

    /** What the interceptor should do with a keyed request. */
    public record Decision(Type type, Long recordId, Integer responseStatus, String responseBody) {

        public enum Type {
            /** First use: run the endpoint; the key is reserved under {@code recordId}. */
            PROCEED,
            /** Same key and request completed before: return the stored response. */
            REPLAY,
            /** Same key, same request, still running. */
            IN_PROGRESS,
            /** Same key, different request. */
            REUSED
        }

        static Decision proceed(Long id) {
            return new Decision(Type.PROCEED, id, null, null);
        }

        static Decision of(Type type) {
            return new Decision(type, null, null, null);
        }
    }

    /**
     * Reserves the key or reports why it can't be used. A concurrent request
     * inserting the same key between the lookup and the insert surfaces as a
     * {@code DataIntegrityViolationException} from the unique constraint; the
     * caller treats that as {@link Decision.Type#IN_PROGRESS}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Decision reserve(Long organizationId, Long projectId, String key, String fingerprint) {
        Instant now = Instant.now();
        Optional<IdempotencyRecord> existing = repository.findByScope(organizationId, projectId, key);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (isExpired(record, now)) {
                repository.delete(record);
                repository.flush();
            } else if (!record.getRequestFingerprint().equals(fingerprint)) {
                return Decision.of(Decision.Type.REUSED);
            } else if (record.getState() == IdempotencyRecord.State.COMPLETED) {
                return new Decision(Decision.Type.REPLAY, record.getId(),
                        record.getResponseStatus(), record.getResponseBody());
            } else {
                return Decision.of(Decision.Type.IN_PROGRESS);
            }
        }

        IdempotencyRecord record = new IdempotencyRecord();
        record.setOrganizationId(organizationId);
        record.setProjectId(projectId);
        record.setIdempotencyKey(key);
        record.setRequestFingerprint(fingerprint);
        record.setState(IdempotencyRecord.State.IN_PROGRESS);
        record.setCreatedAt(now);
        return Decision.proceed(repository.saveAndFlush(record).getId());
    }

    /** Stores a successful response for replay. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long recordId, int responseStatus, String responseBody) {
        repository.markCompleted(recordId, IdempotencyRecord.State.COMPLETED, responseStatus, responseBody, Instant.now());
    }

    /** Frees the key after a failed request, so the client can retry with it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Long recordId) {
        repository.deleteById(recordId);
    }

    /** Hourly clean-up of keys past their replay window. */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 60_000L)
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteCreatedBefore(Instant.now().minus(properties.getTtl()));
        if (deleted > 0) {
            log.info("Purged {} expired idempotency key(s)", deleted);
        }
    }

    private boolean isExpired(IdempotencyRecord record, Instant now) {
        return record.getState() == IdempotencyRecord.State.COMPLETED
                ? record.getCreatedAt().isBefore(now.minus(properties.getTtl()))
                : record.getCreatedAt().isBefore(now.minus(properties.getInProgressTimeout()));
    }
}
