package com.aigreentick.services.template.infrastructure.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.aigreentick.services.template.application.dto.command.ReconcileSubmittedCommand;
import com.aigreentick.services.template.application.service.SubmittedTemplateReconciler;
import com.aigreentick.services.template.infrastructure.config.properties.TemplateReconcileProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Timer that triggers {@link SubmittedTemplateReconciler}. It is an inbound
 * adapter, the scheduled counterpart of a controller: it owns <em>when</em>
 * and <em>with which settings</em>; the reconciler owns <em>what</em>.
 *
 * <p>Everything is configured under {@code template.reconcile.*}
 * ({@link TemplateReconcileProperties}). {@code @Scheduled} reads the interval
 * and initial delay through property placeholders because annotation values
 * must be compile-time constants; the same keys are declared (and validated)
 * on the properties class.
 *
 * <p>{@code fixedDelay}: the next run starts one interval after the previous
 * one finishes, so runs never overlap on one instance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateReconcileScheduler {

    private final SubmittedTemplateReconciler reconciler;
    private final TemplateReconcileProperties properties;

    @Scheduled(fixedDelayString = "${template.reconcile.interval:PT5M}",
            initialDelayString = "${template.reconcile.initial-delay:PT2M}")
    public void run() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            reconciler.reconcile(new ReconcileSubmittedCommand(
                    properties.getMinAge(),
                    properties.getGiveUpAfter(),
                    properties.getBatchSize()));
        } catch (RuntimeException ex) {
            // Never let an exception escape a @Scheduled method: log it and
            // try again next interval.
            log.error("[RECONCILE] run failed; will retry next interval", ex);
        }
    }
}
