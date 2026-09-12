package com.mcpgateway.service.impl;

import com.mcpgateway.domain.entity.AuditEvent;
import com.mcpgateway.repository.AuditEventRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.SecurityUtils;
import com.mcpgateway.service.intf.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Writes audit rows in the caller's transaction so an audit trail cannot outlive a rollback. */
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, Long entityId, Map<String, Object> metadata) {
        recordAs(SecurityUtils.currentUserId().orElse(null),
                SecurityUtils.currentActorLabel(), action, entityType, entityId, metadata);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAs(Long actorId, String actorLabel, String action,
                         String entityType, Long entityId, Map<String, Object> metadata) {

        AuditEvent event = AuditEvent.builder()
                .actor(actorId == null ? null : userRepository.findById(actorId).orElse(null))
                .actorLabel(actorLabel)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .metadata(metadata == null ? Map.of() : metadata)
                .build();

        auditEventRepository.save(event);
    }
}
