package com.mcpgateway.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Credentials or token rejected.
 *
 * <p>The message is intentionally vague so it cannot be used to discover which
 * accounts exist.
 */
public class AuthenticationFailedException extends ApiException {

    public AuthenticationFailedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", message);
    }
}
