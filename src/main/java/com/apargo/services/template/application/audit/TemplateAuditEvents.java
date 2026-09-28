package com.apargo.services.template.application.audit;

import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.apargo.services.template.application.dto.AuditContext;
import com.apargo.services.template.application.port.out.AuditContextProvider;
import com.apargo.services.template.common.constant.AuditConstants;
import com.apargo.services.template.common.constant.AuditConstants.EntityTypes;
import com.apargo.services.template.common.constant.AuditConstants.ErrorCodes;
import com.apargo.services.template.common.constant.AuditConstants.Fields;
import com.apargo.services.template.common.constant.AuditConstants.Messages;
import com.apargo.services.template.common.constant.AuditConstants.Metadata;
import com.apargo.services.template.common.error.ErrorCode;
import com.apargo.services.template.common.exception.BaseApplicationException;
import com.apargo.services.template.common.util.helper.ExceptionCauses;
import com.apargo.services.template.domain.enums.TemplateAuditEventType;
import com.apargo.services.template.domain.model.SystemTemplate;
import com.apargo.services.template.domain.model.WhatsappTemplate;
import com.apargo.platform.contract.audit.AuditEntityDto;
import com.apargo.platform.contract.audit.AuditErrorCategory;
import com.apargo.platform.contract.audit.AuditErrorDto;
import com.apargo.platform.contract.audit.AuditEventDto;
import com.apargo.platform.contract.audit.AuditEventStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The one place this service builds audit events (Audit Producer Guide §6).
 *
 * <p>Each method builds one {@link AuditEventDto} and raises it as a Spring
 * application event. {@code AuditEventPublisher} picks it up <em>after the
 * surrounding transaction commits</em> (immediately when there is none, which
 * is the case for the Meta submission flow, whose writes each commit on their
 * own) and hands it to Kafka off the request thread. A rolled-back
 * transaction therefore never produces an event.
 *
 * <p>Call these only after the change has been written. Every method is
 * failure-proof: a bug here is logged at ERROR and never reaches the business
 * request.
 *
 * <p>What goes in an event: ids, enum names and short user-facing text only.
 * Never the submission payload, Meta's raw response, access tokens or stack
 * traces.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateAuditEvents {

    /** What happened on Meta's side when a template was deleted locally. */
    public enum MetaDeletion {
        /** {@code deleteFromMeta=false}. */
        NOT_REQUESTED,
        /** Requested, but the template was never on Meta (no Meta id). */
        NOT_ON_META,
        DELETED,
        /** Requested and attempted, but Meta or waba-service failed; local delete still happened. */
        FAILED
    }

    /** How a successful submission ended. */
    public enum SubmissionOutcome {
        /** Meta returned an id and a status. */
        ACCEPTED,
        /** Meta's answer was lost (timeout, 5xx, save error); the reconciler will settle it. */
        PENDING_RECONCILIATION
    }

    private final AuditContextProvider contextProvider;
    private final ApplicationEventPublisher events;

    // ----------------------------------------------------
    // Templates
    // ----------------------------------------------------

    /** A template row now exists (as DRAFT, or as SUBMITTED on its way to Meta). */
    public void templateCreated(WhatsappTemplate template) {
        raise(() -> templateEvent(TemplateAuditEventType.TEMPLATE_CREATED, AuditEventStatus.SUCCESS,
                TemplateAuditSnapshot.of(template))
                .metadata(Metadata.STATUS, name(template.getStatus()))
                .metadata(Metadata.CATEGORY, name(template.getCategory()))
                .metadata(Metadata.LANGUAGE, template.getLanguage())
                .build());
    }

    /** A draft's content was replaced. Components and variables are reported as a flag, not diffed. */
    public void templateUpdated(TemplateAuditSnapshot before, WhatsappTemplate after) {
        raise(() -> {
            TemplateAuditSnapshot now = TemplateAuditSnapshot.of(after);
            AuditEventDto.Builder b = templateEvent(TemplateAuditEventType.TEMPLATE_UPDATED,
                    AuditEventStatus.SUCCESS, now)
                    .metadata(Metadata.CONTENT_REPLACED, true);
            diffContent(b, before, now);
            return b.build();
        });
    }

    /**
     * A submission to Meta ended without a definitive rejection.
     *
     * @param before the template before the submit (DRAFT for a submitted
     *               draft, SUBMITTED for a create that submits straight away)
     */
    public void templateSubmitted(TemplateAuditSnapshot before, WhatsappTemplate after, SubmissionOutcome outcome) {
        raise(() -> {
            TemplateAuditSnapshot now = TemplateAuditSnapshot.of(after);
            AuditEventDto.Builder b = templateEvent(TemplateAuditEventType.TEMPLATE_SUBMITTED,
                    AuditEventStatus.SUCCESS, now)
                    .metadata(Metadata.META_OUTCOME, outcome.name())
                    .metadata(Metadata.META_TEMPLATE_ID, now.metaTemplateId());
            diffMetaState(b, before, now);
            return b.build();
        });
    }

    /** Meta definitively rejected the submission; {@code message} is Meta's user-facing reason. */
    public void templateSubmitRejected(TemplateAuditSnapshot before, WhatsappTemplate after, String message) {
        submitFailed(before, after, AuditErrorCategory.EXTERNAL_SERVICE, ErrorCodes.META_REJECTED, message);
    }

    /** The submission failed before or while reaching Meta, and the row was marked FAILED. */
    public void templateSubmitFailed(TemplateAuditSnapshot before, WhatsappTemplate after, Throwable cause) {
        raise(() -> failedSubmission(before, after, errorFrom(cause)));
    }

    /** The reconciler gave up: Meta never received the submission. */
    public void templateSubmissionNotReceived(TemplateAuditSnapshot before, WhatsappTemplate after) {
        submitFailed(before, after, AuditErrorCategory.EXTERNAL_SERVICE, ErrorCodes.SUBMISSION_NOT_RECEIVED,
                Messages.SUBMISSION_NOT_RECEIVED);
    }

    public void templateDeleted(TemplateAuditSnapshot deleted, MetaDeletion metaDeletion) {
        raise(() -> templateEvent(TemplateAuditEventType.TEMPLATE_DELETED, AuditEventStatus.SUCCESS, deleted)
                .metadata(Metadata.META_DELETION, metaDeletion.name())
                .metadata(Metadata.META_TEMPLATE_ID, deleted.metaTemplateId())
                .build());
    }

    public void templatesBulkDeleted(Long organizationId, Long projectId, int deletedCount) {
        raise(() -> base(TemplateAuditEventType.TEMPLATE_BULK_DELETED, AuditEventStatus.SUCCESS)
                .orgId(organizationId)
                .projectId(projectId)
                .metadata(Metadata.DELETED_COUNT, deletedCount)
                .build());
    }

    /**
     * Meta changed a template's status, category or rejection reason, as
     * found by a sync or by the reconciler. Nothing is raised when nothing
     * audited changed. The event type follows the new status
     * (APPROVED / REJECTED / PAUSED / DISABLED, else STATUS_CHANGED), then a
     * category-only change, then anything else as UPDATED.
     */
    public void templateChangedOnMeta(TemplateAuditSnapshot before, WhatsappTemplate after) {
        raise(() -> {
            TemplateAuditSnapshot now = TemplateAuditSnapshot.of(after);
            if (before.status() == now.status()
                    && before.category() == now.category()
                    && Objects.equals(before.rejectionReason(), now.rejectionReason())
                    && Objects.equals(before.metaTemplateId(), now.metaTemplateId())) {
                return null;
            }
            AuditEventDto.Builder b = templateEvent(metaChangeType(before, now), AuditEventStatus.SUCCESS, now)
                    .metadata(Metadata.META_TEMPLATE_ID, now.metaTemplateId());
            diffMetaState(b, before, now);
            return b.build();
        });
    }

    public void templatesSynced(Long organizationId, Long projectId, String wabaId,
                                int inserted, int updated, int deleted) {
        raise(() -> base(TemplateAuditEventType.TEMPLATE_SYNCED, AuditEventStatus.SUCCESS)
                .orgId(organizationId)
                .projectId(projectId)
                .metadata(Metadata.WABA_ID, wabaId)
                .metadata(Metadata.INSERTED, inserted)
                .metadata(Metadata.UPDATED, updated)
                .metadata(Metadata.DELETED, deleted)
                .build());
    }

    /** A user-requested sync (already answered 202) failed in the background. */
    public void templateSyncFailed(Long organizationId, Long projectId, String wabaId, Throwable cause) {
        raise(() -> {
            AuditContext ctx = contextProvider.current();
            return base(ctx, TemplateAuditEventType.TEMPLATE_SYNCED, AuditEventStatus.FAILURE)
                    .orgId(organizationId)
                    .projectId(projectId)
                    .metadata(Metadata.WABA_ID, wabaId)
                    .error(withReference(errorFrom(cause), ctx))
                    .build();
        });
    }

    // ----------------------------------------------------
    // Template Library (platform-level: orgId 0, no project)
    // ----------------------------------------------------

    public void systemTemplateCreated(SystemTemplate created) {
        raise(() -> systemTemplateEvent(TemplateAuditEventType.SYSTEM_TEMPLATE_CREATED,
                SystemTemplateAuditSnapshot.of(created))
                .metadata(Metadata.CATEGORY, name(created.getCategory()))
                .metadata(Metadata.LANGUAGE, created.getLanguage())
                .metadata(Metadata.ACTIVE, created.isActive())
                .build());
    }

    public void systemTemplateUpdated(SystemTemplateAuditSnapshot before, SystemTemplate after) {
        raise(() -> {
            SystemTemplateAuditSnapshot now = SystemTemplateAuditSnapshot.of(after);
            AuditEventDto.Builder b = systemTemplateEvent(TemplateAuditEventType.SYSTEM_TEMPLATE_UPDATED, now)
                    .metadata(Metadata.PAYLOAD_CHANGED, !Objects.equals(before.payload(), now.payload()));
            change(b, Fields.NAME, before.name(), now.name());
            change(b, Fields.LANGUAGE, before.language(), now.language());
            change(b, Fields.CATEGORY, name(before.category()), name(now.category()));
            change(b, Fields.DESCRIPTION, before.description(), now.description());
            change(b, Fields.SAMPLE_MEDIA_URL, before.sampleMediaUrl(), now.sampleMediaUrl());
            change(b, Fields.ACTIVE, before.active(), now.active());
            return b.build();
        });
    }

    // ----------------------------------------------------
    // Building blocks
    // ----------------------------------------------------

    private void submitFailed(TemplateAuditSnapshot before, WhatsappTemplate after,
                              AuditErrorCategory category, String code, String message) {
        raise(() -> failedSubmission(before, after, new AuditErrorDto(category, code, capped(message), null, null)));
    }

    private AuditEventDto failedSubmission(TemplateAuditSnapshot before, WhatsappTemplate after, AuditErrorDto error) {
        AuditContext ctx = contextProvider.current();
        TemplateAuditSnapshot now = TemplateAuditSnapshot.of(after);
        AuditEventDto.Builder b = templateEvent(ctx, TemplateAuditEventType.TEMPLATE_SUBMITTED,
                AuditEventStatus.FAILURE, now);
        diffMetaState(b, before, now);
        return b.error(withReference(error, ctx)).build();
    }

    /** Context, module, type and status: the part every event shares. */
    private AuditEventDto.Builder base(TemplateAuditEventType type, AuditEventStatus status) {
        return base(contextProvider.current(), type, status);
    }

    private static AuditEventDto.Builder base(AuditContext ctx, TemplateAuditEventType type, AuditEventStatus status) {
        return AuditEventDto.builder()
                .sourceService(ctx.sourceService())
                .environment(ctx.environment())
                .module(AuditConstants.MODULE)
                .eventType(type.name())
                .status(status)
                .actor(ctx.actor())
                .channel(ctx.channel())
                .requestId(ctx.requestId())
                .traceId(ctx.traceId())
                .ip(ctx.ip())
                .userAgent(ctx.userAgent());
    }

    private AuditEventDto.Builder templateEvent(TemplateAuditEventType type, AuditEventStatus status,
                                                TemplateAuditSnapshot t) {
        return templateEvent(contextProvider.current(), type, status, t);
    }

    private static AuditEventDto.Builder templateEvent(AuditContext ctx, TemplateAuditEventType type,
                                                       AuditEventStatus status, TemplateAuditSnapshot t) {
        return base(ctx, type, status)
                .orgId(t.organizationId())
                .projectId(t.projectId())
                .entity(new AuditEntityDto(EntityTypes.TEMPLATE, t.id() != null ? String.valueOf(t.id()) : null, t.name()))
                .metadata(Metadata.WABA_ID, t.wabaId());
    }

    private AuditEventDto.Builder systemTemplateEvent(TemplateAuditEventType type, SystemTemplateAuditSnapshot s) {
        return base(type, AuditEventStatus.SUCCESS)
                .orgId(AuditEventDto.PLATFORM_ORG_ID)
                .entity(new AuditEntityDto(EntityTypes.SYSTEM_TEMPLATE,
                        s.id() != null ? String.valueOf(s.id()) : null, s.name()));
    }

    /** Fields a user edits on a draft. */
    private static void diffContent(AuditEventDto.Builder b, TemplateAuditSnapshot before, TemplateAuditSnapshot now) {
        change(b, Fields.NAME, before.name(), now.name());
        change(b, Fields.LANGUAGE, before.language(), now.language());
        change(b, Fields.CATEGORY, name(before.category()), name(now.category()));
        change(b, Fields.WABA_ID, before.wabaId(), now.wabaId());
    }

    /** Fields Meta decides. */
    private static void diffMetaState(AuditEventDto.Builder b, TemplateAuditSnapshot before, TemplateAuditSnapshot now) {
        change(b, Fields.STATUS, name(before.status()), name(now.status()));
        change(b, Fields.CATEGORY, name(before.category()), name(now.category()));
        change(b, Fields.META_TEMPLATE_ID, before.metaTemplateId(), now.metaTemplateId());
        change(b, Fields.REJECTION_REASON, before.rejectionReason(), now.rejectionReason());
    }

    private static TemplateAuditEventType metaChangeType(TemplateAuditSnapshot before, TemplateAuditSnapshot now) {
        if (before.status() != now.status()) {
            if (now.status() == null) {
                return TemplateAuditEventType.TEMPLATE_STATUS_CHANGED;
            }
            return switch (now.status()) {
                case APPROVED -> TemplateAuditEventType.TEMPLATE_APPROVED;
                case REJECTED -> TemplateAuditEventType.TEMPLATE_REJECTED;
                case PAUSED -> TemplateAuditEventType.TEMPLATE_PAUSED;
                case DISABLED -> TemplateAuditEventType.TEMPLATE_DISABLED;
                default -> TemplateAuditEventType.TEMPLATE_STATUS_CHANGED;
            };
        }
        if (before.category() != now.category()) {
            return TemplateAuditEventType.TEMPLATE_CATEGORY_CHANGED;
        }
        return TemplateAuditEventType.TEMPLATE_UPDATED;
    }

    private static void change(AuditEventDto.Builder b, String field, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            b.change(field, capped(oldValue), capped(newValue));
        }
    }

    /**
     * Events stay small: free text (Meta's rejection reason, a description)
     * is cut at {@link AuditConstants#MAX_TEXT_LENGTH} characters. Such text can fall back to a raw
     * upstream body when Meta's answer is not the usual JSON.
     */
    private static Object capped(Object value) {
        return value instanceof String text ? capped(text) : value;
    }

    private static String capped(String text) {
        return text != null && text.length() > AuditConstants.MAX_TEXT_LENGTH
                ? text.substring(0, AuditConstants.MAX_TEXT_LENGTH) + AuditConstants.TRUNCATION_SUFFIX
                : text;
    }

    private static String name(Enum<?> value) {
        return value != null ? value.name() : null;
    }

    /** {@code error.reference} is the event's trace id (guide §3). */
    private static AuditErrorDto withReference(AuditErrorDto error, AuditContext ctx) {
        return new AuditErrorDto(error.category(), error.code(), error.message(), error.details(),
                ctx.traceId());
    }

    /**
     * Error for an exception. Business and validation messages are written
     * for users and are kept; for anything else only the code is kept and
     * the message is generic, because upstream and system messages can carry
     * URLs, response bodies or internals. The full exception is in the logs,
     * findable by the trace id in {@code reference}.
     */
    static AuditErrorDto errorFrom(Throwable cause) {
        if (cause instanceof BaseApplicationException app) {
            ErrorCode code = app.getErrorCode();
            AuditErrorCategory category = categoryOf(code);
            String message = switch (category) {
                case BUSINESS, VALIDATION -> capped(app.getMessage());
                case EXTERNAL_SERVICE -> Messages.UPSTREAM_FAILED.formatted(code.name());
                default -> Messages.ACTION_FAILED.formatted(code.name());
            };
            return new AuditErrorDto(category, code.name(), message, null, null);
        }
        if (ExceptionCauses.hasTimeout(cause)) {
            return new AuditErrorDto(AuditErrorCategory.EXTERNAL_SERVICE, ErrorCode.TIMEOUT.name(),
                    Messages.UPSTREAM_TIMEOUT, null, null);
        }
        return new AuditErrorDto(AuditErrorCategory.SYSTEM, ErrorCode.INTERNAL_ERROR.name(),
                Messages.UNEXPECTED_FAILURE, null, null);
    }

    static AuditErrorCategory categoryOf(ErrorCode code) {
        return switch (code) {
            case VALIDATION_FAILED, BAD_REQUEST, IDEMPOTENCY_KEY_REQUIRED -> AuditErrorCategory.VALIDATION;
            case UNAUTHENTICATED -> AuditErrorCategory.AUTHENTICATION;
            case DEPENDENCY_FAILURE, TIMEOUT, WABA_CREDENTIALS_UNAVAILABLE, MEDIA_UPLOAD_FAILED ->
                    AuditErrorCategory.EXTERNAL_SERVICE;
            case INTERNAL_ERROR -> AuditErrorCategory.SYSTEM;
            default -> AuditErrorCategory.BUSINESS;
        };
    }

    /**
     * Builds and raises one event; never throws. A {@code null} from the
     * supplier means "nothing to audit".
     */
    private void raise(Supplier<AuditEventDto> builder) {
        try {
            AuditEventDto event = builder.get();
            if (event != null) {
                events.publishEvent(event);
            }
        } catch (RuntimeException ex) {
            log.error("Could not build or raise an audit event; the business action is unaffected", ex);
        }
    }
}
