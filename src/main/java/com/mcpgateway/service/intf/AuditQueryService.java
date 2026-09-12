package com.mcpgateway.service.intf;

import com.mcpgateway.dto.response.AuditEventResponse;

import java.util.List;

/**
 * Read side of the audit trail.
 *
 * <p>Separate from {@code AuditService}, which only writes: the writer is a dependency
 * of nearly every service, and it should not drag query methods along with it.
 */
public interface AuditQueryService {

    List<AuditEventResponse> recent(int limit);
}
