package com.aigreentick.services.template.common.logging;

import com.aigreentick.services.template.common.constant.ApiHeaders;
import com.aigreentick.services.template.common.constant.LogKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Establishes the request context (API Standard §1, §2) for every request,
 * on every path, before anything else runs.
 *
 * <ul>
 *   <li><b>{@code X-Request-Id}</b> — reused when the caller sent a
 *       well-formed value, generated otherwise, and echoed on the response
 *       (including 204s and errors). It is the only tracking header.</li>
 *   <li><b>MDC</b> — request id, org, project and user are put in the MDC.
 *       That one carrier feeds the log pattern, {@code meta.requestId} in the
 *       response wrapper, the outbound client filter that forwards these
 *       headers to sibling services, and (via the sync pool's task
 *       decorator) background work started by this request.</li>
 * </ul>
 *
 * <h2>Why the incoming id is validated</h2>
 *
 * The value is written into every log line and echoed in a response header.
 * Accepting arbitrary text would let a caller forge log entries or inflate
 * every line; anything outside {@link #VALID_REQUEST_ID} is replaced with a
 * fresh id rather than rejected, because tracking must never fail a request.
 *
 * <h2>Why it is not called RequestContextFilter</h2>
 *
 * Spring Boot registers its own {@code requestContextFilter} bean
 * ({@code WebMvcAutoConfiguration}). A {@code @Component} of that simple name
 * gets the same bean name, and bean overriding is off, so the context would
 * fail to start. storage-service hit exactly this; the names are aligned now.
 *
 * <h2>Why the MDC is always cleared</h2>
 *
 * Servlet containers pool threads. Without the {@code finally} block the next
 * request on this thread would log under the previous caller's tenant.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /** UUIDs, ULIDs and similar opaque ids; nothing that can break a log line. */
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // On an error dispatch the response already carries the id chosen on
        // the original dispatch; reuse it so /error reports the same id.
        String alreadySent = response.getHeader(ApiHeaders.REQUEST_ID);
        String requestId = alreadySent != null
                ? alreadySent
                : resolveRequestId(request.getHeader(ApiHeaders.REQUEST_ID));

        try {
            MDC.put(LogKeys.REQUEST_ID, requestId);
            putIfPresent(LogKeys.ORG_ID, request.getHeader(ApiHeaders.ORG_ID));
            putIfPresent(LogKeys.PROJECT_ID, request.getHeader(ApiHeaders.PROJECT_ID));
            putIfPresent(LogKeys.USER_ID, request.getHeader(ApiHeaders.USER_ID));

            response.setHeader(ApiHeaders.REQUEST_ID, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Also run on error dispatches (to {@code /error}): the container forwards
     * there after this filter's {@code finally} has cleared the MDC, and the
     * error body still needs {@code meta.requestId}.
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    static String resolveRequestId(String candidate) {
        if (candidate != null && VALID_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Header values are length-capped before they reach the MDC: they are
     * caller-controlled and printed on every log line. An absent value stays
     * absent, so the log pattern prints it as empty rather than "null".
     */
    private void putIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value.length() > 64 ? value.substring(0, 64) : value);
        }
    }
}
