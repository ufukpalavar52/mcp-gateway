package com.mcpgateway.dto.response;

import java.time.Instant;
import java.util.List;

/** A host group with the number of definitions that target it. */
public record HostGroupResponse(Long id,
                                String name,
                                String description,
                                List<String> hosts,
                                int hostCount,
                                long usedByCount,
                                Instant updatedAt) {
}
