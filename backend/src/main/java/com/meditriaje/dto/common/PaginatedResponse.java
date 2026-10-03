package com.meditriaje.dto.common;

import java.util.List;

/**
 * Contenedor genérico para respuestas paginadas del sistema.
 *
 * @param <T> Tipo de elemento contenido en la página.
 */
public record PaginatedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public static <T> PaginatedResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        int totalPages = totalElements > 0 ? (int) Math.ceil((double) totalElements / safeSize) : 0;
        boolean hasNext = (safePage + 1) < totalPages;
        boolean hasPrevious = safePage > 0 && totalPages > 0;

        return new PaginatedResponse<>(
                content != null ? content : List.of(),
                safePage,
                safeSize,
                totalElements,
                totalPages,
                hasNext,
                hasPrevious
        );
    }
}
