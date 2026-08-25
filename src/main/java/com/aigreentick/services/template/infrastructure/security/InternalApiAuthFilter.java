package com.aigreentick.services.template.infrastructure.security;

import com.aigreentick.services.template.api.response.error.ErrorResponse;
import com.aigreentick.services.template.common.constant.InternalHeaders;
import com.aigreentick.services.template.common.constant.LogKeys;
import com.aigreentick.services.template.common.error.ErrorCode;
import com.aigreentick.services.template.infrastructure.config.properties.InternalApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Guards every request under {@code internal.api.path-prefix}.
 *
 * <h2>Why a plain servlet filter</h2>
 *
 * This service has no Spring Security on the classpath, and adding it for one
 * shared-secret check would bring an entire filter chain and a default-deny
 * posture the existing public controllers are not written for. A
 * {@link OncePerRequestFilter} scoped to one path prefix does exactly one job
 * and changes nothing else. If Spring Security is adopted later, this logic
 * becomes an {@code AuthenticationFilter} and this class goes away.
 *
 * <p>The implementation deliberately mirrors waba-service's filter of the
 * same name. Two internal surfaces that authenticate differently is a
 * needless thing for an operator to have to remember.
 *
 * <h2>This is one layer, not the whole defence</h2>
 *
 * <ol>
 *   <li>API gateway refuses to route {@code /internal/**} from outside.</li>
 *   <li>Network policy allows the port only from sibling service subnets.
 *       Note this service registers with Eureka, so anything already in the
 *       mesh can reach it — placement alone proves nothing about which
 *       service is calling.</li>
 *   <li>This filter, on a rotatable shared secret.</li>
 *   <li>Per-request tenant scoping in the use cases.</li>
 * </ol>
 *
 * <h2>Upgrade path</h2>
 *
 * A single shared key cannot tell callers apart: any holder can assert any
 * organization. Move to per-caller keys when you need to revoke one service
 * without rotating all of them, and to mTLS or a gateway-signed JWT when the
 * organization must be a cryptographically verified claim rather than a
 * self-asserted header.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalApiAuthFilter extends OncePerRequestFilter {

    private final InternalApiProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(properties.getPathPrefix());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (properties.isAuthEnabled() && !hasValidKey(request)) {
            // No detail in the response and no echo of the presented key - a
            // caller that failed auth learns only that it failed.
            log.warn("Rejected internal request to {} from caller={}",
                    request.getRequestURI(), request.getHeader(InternalHeaders.CALLER_SERVICE));
            writeUnauthorized(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Compared with {@link MessageDigest#isEqual} rather than
     * {@link String#equals} so comparison time does not vary with how many
     * leading characters happen to match. {@code String.equals} returns on
     * the first differing byte, which leaks the correct prefix to anyone able
     * to measure response times precisely enough.
     */
    private boolean hasValidKey(HttpServletRequest request) {
        String presented = request.getHeader(InternalHeaders.API_KEY);
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                properties.getApiKey().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Written directly rather than by throwing, because a filter runs outside
     * the dispatcher and {@code @RestControllerAdvice} never sees it. Using
     * the same {@link ErrorResponse} envelope means a caller parses one shape
     * whether the failure came from here or from a controller.
     */
    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ErrorResponse body = ErrorResponse.builder()
                .status("ERROR")
                .code(HttpStatus.UNAUTHORIZED.value())
                .errorCode(ErrorCode.UNAUTHORIZED)
                .message("Invalid or missing internal API key")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .traceId(MDC.get(LogKeys.TRACE_ID))
                .build();

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
