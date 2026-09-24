package com.aigreentick.services.template.infrastructure.idempotency;

import com.aigreentick.services.template.common.constant.ApiHeaders;
import com.aigreentick.services.template.common.constant.LogKeys;
import com.aigreentick.services.template.common.error.ErrorCode;
import com.aigreentick.services.template.common.exception.IdempotencyKeyException;
import com.aigreentick.services.template.common.web.Idempotent;
import com.aigreentick.services.template.infrastructure.config.properties.IdempotencyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Applies {@code X-Idempotency-Key} to handlers marked {@link Idempotent}.
 *
 * <ol>
 *   <li>{@code preHandle}: reserve the key (scoped to org + project). If it
 *       was already completed for the same request, write the stored
 *       response and skip the handler.</li>
 *   <li>{@code afterCompletion}: on a 2xx, store the response; otherwise
 *       free the key so the client can retry with it.</li>
 * </ol>
 *
 * <p>Errors are raised as {@link IdempotencyKeyException}; exceptions from
 * {@code preHandle} go through {@code GlobalExceptionHandler}, so they come
 * back in the standard wrapper like any other error.
 *
 * <p>Invalid tenancy headers are left for the controller to reject (400);
 * the key can't be scoped without them, so it is not processed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final String RECORD_ATTRIBUTE = IdempotencyInterceptor.class.getName() + ".recordId";
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final IdempotencyStore store;
    private final IdempotencyProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws IOException {

        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(Idempotent.class)) {
            return true;
        }

        String key = request.getHeader(ApiHeaders.IDEMPOTENCY_KEY);
        if (key == null || key.isBlank()) {
            if (properties.isRequired()) {
                throw new IdempotencyKeyException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                        "Missing required header '" + ApiHeaders.IDEMPOTENCY_KEY + "'");
            }
            return true;
        }
        if (!VALID_KEY.matcher(key).matches()) {
            throw new IdempotencyKeyException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Invalid header '" + ApiHeaders.IDEMPOTENCY_KEY
                    + "': use 1-128 letters, digits or . _ : - (a UUID is ideal)");
        }

        Long organizationId = parsePositive(request.getHeader(ApiHeaders.ORG_ID));
        Long projectId = parsePositive(request.getHeader(ApiHeaders.PROJECT_ID));
        if (organizationId == null || projectId == null) {
            return true;
        }

        IdempotencyStore.Decision decision;
        try {
            decision = store.reserve(organizationId, projectId, key, fingerprint(request));
        } catch (DataIntegrityViolationException concurrentInsert) {
            decision = new IdempotencyStore.Decision(IdempotencyStore.Decision.Type.IN_PROGRESS, null, null, null);
        }

        switch (decision.type()) {
            case PROCEED -> {
                request.setAttribute(RECORD_ATTRIBUTE, decision.recordId());
                return true;
            }
            case REPLAY -> {
                log.info("Replaying stored response for idempotency key on {}", request.getRequestURI());
                writeReplay(response, decision);
                return false;
            }
            case IN_PROGRESS -> throw new IdempotencyKeyException(ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS,
                    "A request with this idempotency key is still being processed");
            case REUSED -> throw new IdempotencyKeyException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This idempotency key was already used for a different request");
            default -> throw new IllegalStateException("Unhandled decision " + decision.type());
        }
    }

    /**
     * Best effort by design: failing to record the outcome must not fail a
     * request that already succeeded. The cost is that a retry may run the
     * endpoint again, which the template duplicate check still guards.
     */
    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {

        if (!(request.getAttribute(RECORD_ATTRIBUTE) instanceof Long recordId)) {
            return;
        }
        try {
            ContentCachingResponseWrapper cached =
                    WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
            int status = response.getStatus();
            if (ex == null && cached != null && HttpStatusCode.valueOf(status).is2xxSuccessful()) {
                store.complete(recordId, status, new String(cached.getContentAsByteArray(), StandardCharsets.UTF_8));
            } else {
                store.release(recordId);
            }
        } catch (RuntimeException e) {
            log.error("Could not record idempotency outcome for {}", request.getRequestURI(), e);
        }
    }

    /**
     * Returns the stored response with fresh {@code meta}. A stored
     * {@code 201 Created} is replayed as {@code 200 OK}: the standard's
     * "already existed (idempotent create)" row.
     */
    private void writeReplay(HttpServletResponse response, IdempotencyStore.Decision decision) throws IOException {
        int status = decision.responseStatus() == HttpStatus.CREATED.value()
                ? HttpStatus.OK.value()
                : decision.responseStatus();

        String body = decision.responseBody();
        if (body != null && !body.isBlank()) {
            JsonNode node = objectMapper.readTree(body);
            if (node instanceof ObjectNode root) {
                root.put("status", status);
                if (root.get("meta") instanceof ObjectNode meta) {
                    meta.put("requestId", MDC.get(LogKeys.REQUEST_ID));
                    meta.put("timestamp", Instant.now().toString());
                }
                body = objectMapper.writeValueAsString(root);
            }
        }

        response.setStatus(status);
        if (body != null && !body.isBlank()) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(body);
        }
    }

    /** SHA-256 over method, path and body: same key + different request = reuse. */
    private String fingerprint(HttpServletRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(request.getMethod().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ' ');
            digest.update(request.getRequestURI().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            CachedBodyHttpServletRequest cached =
                    WebUtils.getNativeRequest(request, CachedBodyHttpServletRequest.class);
            if (cached != null) {
                digest.update(cached.getBody());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private Long parsePositive(String value) {
        if (value == null) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
