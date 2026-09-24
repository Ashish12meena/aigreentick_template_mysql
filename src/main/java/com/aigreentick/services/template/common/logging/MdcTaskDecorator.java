package com.aigreentick.services.template.common.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Carries the submitting thread's MDC (request id, org, project, user) onto
 * pool threads.
 *
 * <p>Without it, background sync work logs with blank ids and, because the
 * outbound client filter reads the MDC, calls it makes to waba-service and
 * storage-service lose {@code X-Request-Id} and the tenancy headers — the
 * standard requires those to be passed on unchanged.
 *
 * <p>The worker's previous context is restored afterwards rather than simply
 * cleared: with {@code CallerRunsPolicy} the task may run on the submitting
 * thread itself, which must keep its own context.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            setOrClear(captured);
            try {
                runnable.run();
            } finally {
                setOrClear(previous);
            }
        };
    }

    private static void setOrClear(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
