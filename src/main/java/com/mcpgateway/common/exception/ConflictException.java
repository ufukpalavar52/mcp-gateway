package com.mcpgateway.common.exception;

import org.springframework.http.HttpStatus;

/** The request clashes with existing state, for example a duplicate unique value. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
