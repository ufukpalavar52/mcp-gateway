package com.mcpgateway.common.exception;

import org.springframework.http.HttpStatus;

/** The addressed resource does not exist. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", "%s not found: %s".formatted(resource, identifier));
    }

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
