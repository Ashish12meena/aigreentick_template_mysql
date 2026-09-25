package com.aigreentick.services.template.infrastructure.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Settings for the stuck-SUBMITTED reconciliation - bound to
 * {@code template.reconcile.*}. Read by {@code TemplateReconcileScheduler},
 * which passes them to {@code SubmittedTemplateReconciler}.
 *
 * <p>A template is left SUBMITTED when its Meta outcome could not be recorded
 * (timeout, Meta 5xx, crash or DB error after Meta accepted). The reconciler
 * looks such rows up on Meta by name and records what it finds.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "template.reconcile")
public class TemplateReconcileProperties {

    /** Master switch. */
    private boolean enabled = true;

    /**
     * Time between runs (from the end of one run to the start of the next).
     * {@code @Scheduled} reads this via the placeholder
     * {@code template.reconcile.interval}; declared here so it is validated.
     */
    @NotNull
    private Duration interval = Duration.ofMinutes(5);

    /** Delay after startup before the first run. */
    @NotNull
    private Duration initialDelay = Duration.ofMinutes(2);

    /**
     * How long a row must have been SUBMITTED before it is checked. Must be
     * comfortably longer than the Meta read timeout, so an in-flight request
     * is never reconciled underneath itself.
     */
    @NotNull
    private Duration minAge = Duration.ofMinutes(5);

    /**
     * If Meta still has no template with this name after this long, the
     * submission never reached Meta: the row is marked FAILED (freeing the
     * name). Before this, "not found" is treated as "not visible yet".
     */
    @NotNull
    private Duration giveUpAfter = Duration.ofMinutes(30);

    /** Rows checked per run. */
    @Positive
    private int batchSize = 50;

    /** Fails startup on a nonsensical combination instead of failing every run. */
    @AssertTrue(message = "template.reconcile.give-up-after must be >= min-age")
    public boolean isGiveUpAfterNotBeforeMinAge() {
        return minAge == null || giveUpAfter == null || giveUpAfter.compareTo(minAge) >= 0;
    }
}
