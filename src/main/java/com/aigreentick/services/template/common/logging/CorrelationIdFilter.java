package com.aigreentick.services.template.common.logging;

import com.aigreentick.services.template.common.constant.ApiHeaders;
import com.aigreentick.services.template.common.constant.LogKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates the logging MDC for every request, on every path.
 *
 * <h2>Why this runs first</h2>
 *
 * It is ordered {@link Ordered#HIGHEST_PRECEDENCE} so the trace id exists
 * before any other filter can log or reject. In particular
 * {@code InternalApiAuthFilter} rejects unauthenticated callers, and a
 * rejection with no trace id is close to unusable when someone reports that
 * their service "started getting 401s" — there is nothing to correlate
 * against.
 *
 * <h2>Why the id is accepted from the caller</h2>
 *
 * When {@link ApiHeaders#REQUEST_ID} is present the value is reused rather
 * than replaced, so one logical operation carries the same id across
 * template-service, waba-service and storage-service. A fresh id per hop
 * would make a distributed trace impossible to reassemble. When the header is
 * absent — a browser, curl, a service that forgot — one is generated, so the
 * field is never blank.
 *
 * <p>The id is echoed back on the response so a caller can quote it, and it
 * is also placed in every error body by {@code GlobalExceptionHandler}.
 *
 * <h2>Why the MDC is always cleared</h2>
 *
 * Servlet containers pool threads. Without the {@code finally} block a
 * request that failed early would leave its org id in the MDC, and the next
 * unrelated request served by that thread would log under the wrong tenant —
 * a quietly misleading audit trail rather than an outright error.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String traceId = firstNonBlank(request.getHeader(ApiHeaders.REQUEST_ID), newTraceId());

        try {
            MDC.put(LogKeys.TRACE_ID, traceId);
            putIfPresent(LogKeys.ORG_ID, request.getHeader(ApiHeaders.ORG_ID));
            putIfPresent(LogKeys.PROJECT_ID, request.getHeader(ApiHeaders.PROJECT_ID));

            response.setHeader(ApiHeaders.REQUEST_ID, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Tenancy headers are optional on some endpoints, and an absent value
     * must stay absent rather than become the string {@code "null"} — the log
     * pattern renders a missing key as empty, which reads correctly.
     */
    private void putIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    private String firstNonBlank(String candidate, String fallback) {
        return candidate != null && !candidate.isBlank() ? candidate : fallback;
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
