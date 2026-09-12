package com.mcpgateway.client;

import com.mcpgateway.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * The MCP server understood the request and refused it.
 *
 * <p>Distinct from {@link McpServerUnavailableException}, and the distinction is the whole
 * point: a {@code 503} tells the caller to try again, which is right when a dependency is
 * down and wrong when they named a tool that does not exist. The status is passed through
 * so the answer means the same thing at both ends.
 */
public class McpServerRefusedException extends ApiException {

    public McpServerRefusedException(HttpStatusCode status, String message) {
        super(HttpStatus.valueOf(status.value()), "MCP_REQUEST_REFUSED", message);
    }
}
