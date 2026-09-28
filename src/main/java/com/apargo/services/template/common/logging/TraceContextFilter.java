package com.apargo.services.template.common.logging;

import com.apargo.services.template.common.constant.ApiPaths;
import com.apargo.services.template.common.constant.InternalHeaders;
import com.apargo.services.template.common.constant.LogKeys;
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

/**
 * Establishes the backend trace id for every request (Audit Producer Guide
 * §5). Runs right after {@link RequestIdFilter}, which owns clearing the MDC.
 *
 * <ul>
 *   <li><b>Public requests</b> always get a new trace id. A client-sent
 *       {@code traceparent} is ignored: clients never create trace ids, and a
 *       forged one would let a caller splice its requests into someone else's
 *       audit trail. (When the gateway starts creating trace ids and
 *       overwriting client values, public paths can trust it too.)</li>
 *   <li><b>{@code /internal/**}</b> continues the caller's trace when it sends
 *       a well-formed {@code traceparent}; these callers are inside the trust
 *       boundary ({@code InternalApiAuthFilter}).</li>
 * </ul>
 *
 * <p>The id goes into the MDC ({@link LogKeys#TRACE_ID}), from where it
 * reaches every log line, {@code meta.traceId} in the response wrapper, the
 * outbound {@code traceparent} to sibling services, background work (via
 * {@link MdcTaskDecorator}) and every audit event. It is not echoed as a
 * response header: {@code X-Request-Id} stays the only client-facing
 * tracking header.
 *
 * <p>The id is also kept as a request attribute so the error dispatch to
 * {@code /error} (which happens after the MDC was cleared) reports the same
 * trace id as the original dispatch.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TraceContextFilter extends OncePerRequestFilter {

    static final String TRACE_ID_ATTRIBUTE = TraceContextFilter.class.getName() + ".traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Object existing = request.getAttribute(TRACE_ID_ATTRIBUTE);
        String traceId = existing instanceof String s ? s : resolveTraceId(request);
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);

        MDC.put(LogKeys.TRACE_ID, traceId);
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private static String resolveTraceId(HttpServletRequest request) {
        if (isInternal(request)) {
            return TraceParent.parseTraceId(request.getHeader(InternalHeaders.TRACEPARENT))
                    .orElseGet(TraceParent::newTraceId);
        }
        return TraceParent.newTraceId();
    }

    static boolean isInternal(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals(ApiPaths.INTERNAL) || path.startsWith(ApiPaths.INTERNAL + "/");
    }
}
