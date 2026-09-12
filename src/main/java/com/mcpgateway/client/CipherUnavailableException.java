package com.mcpgateway.client;

import com.mcpgateway.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The encryption service could not be reached, or refused.
 *
 * <p>A {@code 503}: nothing is wrong with the request, and a caller that waits and retries
 * may well succeed. Reporting it as a {@code 500} would tell them to change something they
 * have no reason to change.
 */
public class CipherUnavailableException extends ApiException {

    public CipherUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CIPHER_UNAVAILABLE", message);
    }
}
