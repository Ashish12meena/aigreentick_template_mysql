package com.aigreentick.services.template.application.dto.command;

import java.time.Duration;

/**
 * Inputs for one run of {@code SubmittedTemplateReconciler}. Supplied by the
 * caller (the scheduler reads them from configuration), so the application
 * layer does not depend on how or where they are configured.
 *
 * @param minAge      only rows SUBMITTED for at least this long are checked
 * @param giveUpAfter rows still not found on Meta after this long are marked FAILED
 * @param batchSize   maximum rows checked in this run
 */
public record ReconcileSubmittedCommand(Duration minAge, Duration giveUpAfter, int batchSize) {

    public ReconcileSubmittedCommand {
        if (minAge == null || minAge.isNegative()) {
            throw new IllegalArgumentException("minAge must be zero or positive");
        }
        if (giveUpAfter == null || giveUpAfter.compareTo(minAge) < 0) {
            throw new IllegalArgumentException("giveUpAfter must be >= minAge");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }
}
