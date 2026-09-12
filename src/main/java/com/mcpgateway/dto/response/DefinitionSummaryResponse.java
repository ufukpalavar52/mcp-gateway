package com.mcpgateway.dto.response;

import java.time.Instant;
import java.util.Map;

/** List row for the definitions page; actions are summarised as counts per kind. */
public record DefinitionSummaryResponse(Long id,
                                        String name,
                                        String toolName,
                                        String toolDescription,
                                        String modelIdentifier,
                                        Map<String, Long> actionCountsByKind,
                                        int inputCount,
                                        boolean enabled,
                                        Instant updatedAt) {
}
