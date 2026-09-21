package com.mcpgateway.common.exception;

import com.mcpgateway.common.dto.ApiError;
import com.mcpgateway.validation.BusinessRuleValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Turns every exception into the single {@link ApiError} shape.
 *
 * <p>Controllers therefore never catch anything: they either return a value or let
 * the exception travel, which keeps them free of error plumbing.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Domain rule failure raised by the validation aspect.
     *
     * <p>Declared before the generic {@link ApiException} handler so the per-field
     * violations survive into the response instead of collapsing into one message.
     */
    @ExceptionHandler(BusinessRuleValidationException.class)
    public ResponseEntity<ApiError> handleBusinessRules(BusinessRuleValidationException ex,
                                                        HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(ApiError.of(
                ex.getStatus().value(),
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI(),
                ex.getViolations()));
    }

    /**
     * Every deliberate API failure carries its own status and code.
     *
     * <p>Logged when the status says this service failed, and silent when it says the
     * caller did. A tool that was not found or a password that was wrong is ordinary
     * traffic; logging those buries the entries that matter in the ones that do not.
     *
     * <p>Silent for every status was the earlier rule, and it cost a diagnosis. On
     * 21 September a prompt came back "Could not route the prompt: I/O error ... Request
     * cancelled" — a 503 — and the gateway log had nothing at all for that minute: not the
     * error, not the request, nothing. The MCP server's own log showed two model calls
     * answering normally, so the one process that knew why the call was abandoned was the
     * one that said nothing about it. <b>An error a person can read on a screen and not
     * find in a log is an error that cannot be diagnosed twice.</b>
     *
     * <p>The cause travels with it. A {@code RestClientException} wrapped in this carries
     * the only description of what actually went wrong on the wire, and the message alone
     * — "Could not route the prompt" — names the intention rather than the failure.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("{} on {}", ex.getErrorCode(), request.getRequestURI(), ex);
        }

        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getStatus().value(), ex.getErrorCode(), ex.getMessage(), request.getRequestURI()));
    }

    /** Bean validation on a {@code @RequestBody} argument. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest request) {
        List<ApiError.FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();

        // Class level constraints report no field, so they arrive as global errors.
        List<ApiError.FieldError> globals = ex.getBindingResult().getGlobalErrors().stream()
                .map(error -> new ApiError.FieldError(error.getObjectName(), error.getDefaultMessage()))
                .toList();

        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "Request validation failed",
                request.getRequestURI(),
                java.util.stream.Stream.concat(details.stream(), globals.stream()).toList()));
    }

    /** Bean validation triggered programmatically or on path and query parameters. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<ApiError.FieldError> details = ex.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldError(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();

        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "Request validation failed",
                request.getRequestURI(),
                details));
    }

    /**
     * Authenticated request to a path that maps to nothing.
     *
     * <p>Without these two handlers the catch-all below would answer {@code 500} for
     * what is only a wrong URL. The unauthenticated equivalent is decided earlier, by
     * {@code RestAuthenticationEntryPoint}.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNoHandler(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                HttpStatus.NOT_FOUND.value(), "NOT_FOUND",
                "No endpoint matches this path", request.getRequestURI()));
    }

    /** The path exists but does not accept this verb. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiError.of(
                HttpStatus.METHOD_NOT_ALLOWED.value(), "METHOD_NOT_ALLOWED",
                "%s is not supported for this path".formatted(ex.getMethod()),
                request.getRequestURI()));
    }

    /** A path or query value that cannot be converted, such as a non numeric id. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(), "INVALID_PARAMETER",
                "Parameter '%s' has an invalid value".formatted(ex.getName()),
                request.getRequestURI(),
                List.of(new ApiError.FieldError(ex.getName(), "Value cannot be converted"))));
    }

    /** A required query parameter was omitted. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                           HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(), "MISSING_PARAMETER",
                "Parameter '%s' is required".formatted(ex.getParameterName()),
                request.getRequestURI(),
                List.of(new ApiError.FieldError(ex.getParameterName(), "Required"))));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(), "MALFORMED_REQUEST",
                "Request body is missing or malformed", request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                HttpStatus.FORBIDDEN.value(), "ACCESS_DENIED",
                "You are not allowed to perform this action", request.getRequestURI()));
    }

    /**
     * Database constraint that slipped past the service layer. The detail is logged
     * but never returned, because it exposes schema internals.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("Database constraint violated on {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                HttpStatus.CONFLICT.value(), "CONFLICT",
                "The request conflicts with existing data", request.getRequestURI()));
    }

    /**
     * The session store is unreachable or refuses the connection.
     *
     * <p>Reported as {@code 503} rather than {@code 500}: nothing is wrong with the
     * request, a dependency is down, and the caller may retry. Naming it explicitly also
     * saves the next person the hunt this once cost — a wrong Redis password used to
     * surface as an opaque "Unexpected error" on every login.
     */
    @ExceptionHandler({RedisConnectionFailureException.class, RedisSystemException.class})
    public ResponseEntity<ApiError> handleRedisDown(Exception ex, HttpServletRequest request) {
        log.error("Session store unavailable on {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiError.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(), "SESSION_STORE_UNAVAILABLE",
                "Session store is unavailable, please try again shortly",
                request.getRequestURI()));
    }

    /**
     * Any other persistence failure, including a transaction that could not be started.
     *
     * <p>{@link CannotCreateTransactionException} is listed explicitly because it is
     * <em>not</em> a {@link DataAccessException} — it comes from the transaction
     * hierarchy — so without it the one failure that means "the database is gone" fell
     * through to the catch-all and was reported as a {@code 500}. Opening a transaction
     * fails before any statement runs, so nothing about the request is at fault and the
     * caller may retry, which is what a {@code 503} says and a {@code 500} does not.
     *
     * <p>Details stay in the log, never in the response.
     */
    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    public ResponseEntity<ApiError> handleDataAccess(Exception ex,
                                                     HttpServletRequest request) {
        log.error("Data access failure on {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiError.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(), "STORAGE_UNAVAILABLE",
                "A storage backend is unavailable, please try again shortly",
                request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled failure on {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                "Unexpected error", request.getRequestURI()));
    }
}
