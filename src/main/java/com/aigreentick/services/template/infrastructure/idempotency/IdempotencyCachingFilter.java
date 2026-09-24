package com.aigreentick.services.template.infrastructure.idempotency;

import com.aigreentick.services.template.common.constant.ApiHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Buffers what {@link IdempotencyInterceptor} needs: the JSON request body
 * (to fingerprint it) and the response body (to store it for replay).
 *
 * <p>Runs only for POSTs that carry {@code X-Idempotency-Key}; every other
 * request passes through untouched and unbuffered. Multipart bodies are never
 * buffered here.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotencyCachingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || request.getHeader(ApiHeaders.IDEMPOTENCY_KEY) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        HttpServletRequest req = isJson(request) ? new CachedBodyHttpServletRequest(request) : request;
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(req, res);
        } finally {
            res.copyBodyToResponse();
        }
    }

    private boolean isJson(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
