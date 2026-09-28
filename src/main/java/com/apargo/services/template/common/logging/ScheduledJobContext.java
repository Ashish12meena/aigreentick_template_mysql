package com.apargo.services.template.common.logging;

import com.apargo.services.template.common.constant.LogKeys;
import org.slf4j.MDC;

import java.util.Map;

/**
 * The one way a scheduled job starts its context (Audit Producer Guide §5.4):
 * every run gets a <em>new</em> trace id and the job name, so its log lines
 * group together and its audit events carry actor {@code SYSTEM / <job name>}
 * and channel {@code WORKER}. A job run has no request id.
 *
 * <p>The thread's previous MDC is restored afterwards: scheduler threads are
 * pooled, and one run must not leak its trace into the next.
 */
public final class ScheduledJobContext {

    private ScheduledJobContext() {
    }

    public static void run(String jobName, Runnable job) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        MDC.clear();
        MDC.put(LogKeys.TRACE_ID, TraceParent.newTraceId());
        MDC.put(LogKeys.JOB_NAME, jobName);
        try {
            job.run();
        } finally {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }
}
