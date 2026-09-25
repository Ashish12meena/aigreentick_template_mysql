package com.aigreentick.services.template.application.dto.result;

/**
 * Outcome of one reconciliation run.
 *
 * @param checked    rows examined
 * @param accepted   found on Meta; Meta's id and status recorded
 * @param failed     never reached Meta; marked FAILED (name freed)
 * @param unresolved still unknown (not visible on Meta yet, or lookup failed); retried next run
 */
public record ReconcileSubmittedResult(int checked, int accepted, int failed, int unresolved) {

    public static ReconcileSubmittedResult empty() {
        return new ReconcileSubmittedResult(0, 0, 0, 0);
    }
}
