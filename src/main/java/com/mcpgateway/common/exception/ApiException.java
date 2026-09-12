package com.mcpgateway.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base for every failure the API deliberately produces. */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
