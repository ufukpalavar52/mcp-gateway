package com.mcpgateway.client;

import com.mcpgateway.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

/**
 * The MCP server could not be reached or answered unusably.
 *
 * <p>A {@code 503}, not a {@code 500}: nothing is wrong with the request, a dependency
 * is down, and the caller may retry.
 */
public class McpServerUnavailableException extends ApiException {

    public McpServerUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "MCP_SERVER_UNAVAILABLE", message);
    }

    /**
     * Names the underlying failure in the message.
     *
     * <p>The cause is attached as well, but the message is what reaches a client and a
     * log line, and "could not publish" on its own says nothing about whether the MCP
     * server was unreachable, rejected the payload, or timed out.
     */
    public McpServerUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "MCP_SERVER_UNAVAILABLE",
                message + ": " + describe(cause));
        initCause(cause);
    }

    private static String describe(Throwable cause) {
        if (cause instanceof RestClientResponseException response) {
            // The body carries the server's own explanation; without it a 422 says only
            // that something in the payload was wrong, never which part.
            return response.getStatusCode() + " " + response.getResponseBodyAsString();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
