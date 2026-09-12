package com.mcpgateway.dto.response;

import java.time.Instant;
import java.util.Map;

/** One entry of the activity feed. */
public record AuditEventResponse(Long id,
                                 String actorLabel,
                                 String action,
                                 String entityType,
                                 Long entityId,
                                 Map<String, Object> metadata,
                                 Instant createdAt) {
}
