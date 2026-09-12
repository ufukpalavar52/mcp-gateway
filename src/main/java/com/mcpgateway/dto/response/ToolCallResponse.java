package com.mcpgateway.dto.response;

import com.mcpgateway.domain.enums.LogLevel;

import java.time.Instant;

/** One audit row of the log page. */
public record ToolCallResponse(Long id,
                               String toolName,
                               String modelLabel,
                               String actorLabel,
                               LogLevel level,
                               int durationMs,
                               Integer inputTokens,
                               Integer outputTokens,
                               String message,
                               Instant createdAt) {
}
