package com.escapii.dto;

import com.escapii.model.AppError;

import java.time.LocalDateTime;

/**
 * Javni DTO za listu grešaka u admin panelu.
 * Izostavlja stackTrace - vraća se samo na zahtev (detalji jedne greške).
 * Ovo sprečava curenje internih putanja, dependency verzija i stack frames-a
 * u slučaju da admin API bude kompromitovan.
 */
public record AppErrorSummaryResponse(
        Long          id,
        String        endpoint,
        String        exceptionType,
        String        message,
        int           statusCode,
        int           count,
        LocalDateTime firstSeenAt,
        LocalDateTime lastSeenAt,
        boolean       resolved
) {
    public static AppErrorSummaryResponse from(AppError e) {
        return new AppErrorSummaryResponse(
                e.getId(),
                e.getEndpoint(),
                e.getExceptionType(),
                e.getMessage(),
                e.getStatusCode() != null ? e.getStatusCode() : 0,
                e.getCount()      != null ? e.getCount()      : 0,
                e.getFirstSeenAt(),
                e.getLastSeenAt(),
                e.isResolved()
        );
    }
}
