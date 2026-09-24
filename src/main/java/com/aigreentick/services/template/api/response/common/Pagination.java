package com.aigreentick.services.template.api.response.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

/**
 * Page-based paging details for {@code data.pagination} (API Standard §5).
 */
@Schema(description = "Paging details")
public record Pagination(
        @Schema(description = "Page number returned, starting at 0", example = "0") int page,
        @Schema(description = "Page size used", example = "20") int size,
        @Schema(description = "Total matching items across all pages", example = "42") long totalItems,
        @Schema(description = "Total number of pages", example = "3") int totalPages,
        @Schema(example = "true") boolean hasNext,
        @Schema(example = "false") boolean hasPrevious) {

    public static Pagination from(Page<?> page) {
        return new Pagination(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious());
    }
}
