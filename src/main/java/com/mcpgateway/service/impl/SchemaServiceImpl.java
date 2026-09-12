package com.mcpgateway.service.impl;

import com.mcpgateway.common.exception.BusinessRuleException;
import com.mcpgateway.common.exception.ResourceNotFoundException;
import com.mcpgateway.domain.entity.Action;
import com.mcpgateway.domain.entity.Definition;
import com.mcpgateway.domain.entity.Run;
import com.mcpgateway.domain.entity.Secret;
import com.mcpgateway.domain.enums.ActionKind;
import com.mcpgateway.domain.enums.RunStatus;
import com.mcpgateway.domain.json.ActionConfig;
import com.mcpgateway.queue.IntrospectionPublisher;
import com.mcpgateway.repository.DefinitionRepository;
import com.mcpgateway.repository.RunRepository;
import com.mcpgateway.repository.SecretRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.SecurityUtils;
import com.mcpgateway.service.intf.AuditService;
import com.mcpgateway.service.intf.SchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaServiceImpl implements SchemaService {

    private final DefinitionRepository definitionRepository;
    private final RunRepository runRepository;
    private final SecretRepository secretRepository;
    private final UserRepository userRepository;
    private final IntrospectionPublisher publisher;
    private final AuditService auditService;

    @Override
    @Transactional
    public String introspect(Long definitionId, Long actionId) {
        Definition definition = definitionRepository.findWithActionsById(definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Definition", definitionId));

        Action action = definition.getActions().stream()
                .filter(candidate -> candidate.getId().equals(actionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Action", actionId));

        if (action.getKind() != ActionKind.DB) {
            throw new BusinessRuleException("Only a database action has a schema to read");
        }

        ActionConfig config = action.getConfig();

        if (config.getSchemaTables() == null || config.getSchemaTables().isEmpty()) {
            // Refused rather than defaulting to every table. A schema dump is not what this
            // is for, and the model would be worse off with all of them than with none.
            throw new BusinessRuleException(
                    "Name the tables to read first; introspection reads an allow list");
        }

        String runRef = UUID.randomUUID().toString();
        String actionRef = UUID.randomUUID().toString();

        // Recorded before publishing, as with an ordinary dispatch: the answer arrives on
        // the same queue and has to find a row waiting for it.
        runRepository.save(Run.builder()
                .definition(definition)
                .action(action)
                .actor(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .actorLabel(SecurityUtils.currentActorLabel())
                .status(RunStatus.RUNNING)
                .purpose("introspect")
                .runRef(runRef)
                .actionRef(actionRef)
                .startedAt(Instant.now())
                .build());

        Secret password = config.getPasswordSecretId() == null
                ? null
                : secretRepository.findById(config.getPasswordSecretId()).orElse(null);

        publisher.publish(runRef, actionRef, action, password);

        auditService.record("schema.introspection_requested", "definition", definitionId,
                Map.of("action", actionId, "tables", config.getSchemaTables()));

        log.info("Introspection dispatched for action {} ({} table(s))",
                actionId, config.getSchemaTables().size());

        return runRef;
    }
}
