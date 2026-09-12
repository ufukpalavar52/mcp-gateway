package com.mcpgateway.service.intf;

import java.util.Map;

/** Records who changed what. Kept separate so every service can depend on it. */
public interface AuditService {

    /**
     * Appends an audit row.
     *
     * @param action     dotted verb such as {@code definition.created}
     * @param entityType entity family, for example {@code definition}
     * @param entityId   affected row, may be null for actions without a single target
     * @param metadata   extra context; must not contain secrets
     */
    void record(String action, String entityType, Long entityId, Map<String, Object> metadata);

    /**
     * Records an event on behalf of a specific account.
     *
     * <p>Needed where the security context is not populated yet: a login is performed by
     * the account being authenticated, but that only reaches the context after the
     * response is built, so the default lookup would attribute it to "system".
     */
    void recordAs(Long actorId, String actorLabel, String action,
                  String entityType, Long entityId, Map<String, Object> metadata);
}
