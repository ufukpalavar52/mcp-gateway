package com.mcpgateway.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Single error shape for every failure the API returns.
 *
 * @param status    HTTP status code
 * @param error     short machine readable code, for example {@code VALIDATION_FAILED}
 * @param message   human readable summary
 * @param path      request path that failed
 * @param details   per-field problems, empty for non validation errors
 * @param timestamp when the failure was produced
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(int status,
                       String error,
                       String message,
                       String path,
                       List<FieldError> details,
                       Instant timestamp) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(status, error, message, path, List.of(), Instant.now());
    }

    public static ApiError of(int status, String error, String message, String path, List<FieldError> details) {
        return new ApiError(status, error, message, path, details, Instant.now());
    }

    /** One invalid field. */
    public record FieldError(String field, String message) {
    }
}
