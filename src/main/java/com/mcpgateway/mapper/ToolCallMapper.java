package com.mcpgateway.mapper;

import com.mcpgateway.domain.entity.ToolCall;
import com.mcpgateway.dto.response.ToolCallResponse;
import org.springframework.stereotype.Component;

/** Entity to response translation for log rows. */
@Component
public class ToolCallMapper {

    public ToolCallResponse toResponse(ToolCall call) {
        return new ToolCallResponse(
                call.getId(),
                call.getToolName(),
                call.getModelLabel(),
                call.getActorLabel(),
                call.getLevel(),
                call.getDurationMs(),
                call.getInputTokens(),
                call.getOutputTokens(),
                call.getMessage(),
                call.getCreatedAt());
    }
}
