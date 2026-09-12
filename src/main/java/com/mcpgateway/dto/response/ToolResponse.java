package com.mcpgateway.dto.response;

import java.util.Map;

/**
 * One entry of the MCP tool catalogue.
 *
 * <p>{@code inputSchema} is a ready to use JSON Schema built from the definition's
 * dynamic inputs, so the Python MCP server can answer {@code tools/list} by forwarding
 * this payload untouched.
 */
public record ToolResponse(String name,
                           String description,
                           Map<String, Object> inputSchema,
                           Long definitionId,
                           String modelIdentifier,
                           int actionCount,
                           boolean enabled) {
}
