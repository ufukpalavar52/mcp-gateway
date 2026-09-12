package com.mcpgateway.dto.response;

/** How many calls a model served, for the dashboard's distribution chart. */
public record ModelUsageResponse(String modelLabel, long calls) {
}
