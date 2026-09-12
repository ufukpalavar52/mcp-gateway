package com.mcpgateway.service.impl;

import com.mcpgateway.domain.entity.AuditEvent;
import com.mcpgateway.dto.response.AuditEventResponse;
import com.mcpgateway.repository.AuditEventRepository;
import com.mcpgateway.service.intf.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Serves the activity feed. */
@Service
@RequiredArgsConstructor
public class AuditQueryServiceImpl implements AuditQueryService {

    private final AuditEventRepository auditEventRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AuditEventResponse> recent(int limit) {
        return auditEventRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.clamp(limit, 1, 100)))
                .map(this::toResponse)
                .getContent();
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getActorLabel(),
                event.getAction(),
                event.getEntityType(),
                event.getEntityId(),
                event.getMetadata(),
                event.getCreatedAt());
    }
}
