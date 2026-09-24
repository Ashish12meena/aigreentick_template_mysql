package com.aigreentick.services.template.api.response.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * The {@code data} of every list endpoint: {@code {items, pagination}}.
 * {@code data} is never a bare array, and an empty result is
 * {@code items: []} with pagination filled in (API Standard §4, §5).
 *
 * <p>Replaces serializing Spring's {@code Page} directly, whose JSON shape
 * ({@code content}, {@code pageable}, {@code sort}, ...) is an implementation
 * detail Spring itself warns against exposing.
 */
@Schema(description = "A page of items")
public record PageResponse<T>(List<T> items, Pagination pagination) {

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), Pagination.from(page));
    }
}
