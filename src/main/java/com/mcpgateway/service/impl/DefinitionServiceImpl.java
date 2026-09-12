package com.mcpgateway.service.impl;

import com.mcpgateway.common.dto.PageResponse;
import com.mcpgateway.common.exception.ConflictException;
import com.mcpgateway.common.exception.ResourceNotFoundException;
import com.mcpgateway.domain.entity.Action;
import com.mcpgateway.domain.entity.AiModel;
import com.mcpgateway.domain.entity.Definition;
import com.mcpgateway.domain.entity.HostGroup;
import com.mcpgateway.domain.enums.ActionKind;
import com.mcpgateway.domain.json.DefinitionInput;
import com.mcpgateway.client.CipherClient;
import com.mcpgateway.domain.entity.Secret;
import com.mcpgateway.domain.enums.SecretKind;
import com.mcpgateway.domain.json.ActionConfig;
import com.mcpgateway.dto.request.ActionRequest;
import com.mcpgateway.repository.SecretRepository;
import com.mcpgateway.dto.request.DefinitionInputRequest;
import com.mcpgateway.dto.request.DefinitionRequest;
import com.mcpgateway.dto.response.DefinitionResponse;
import com.mcpgateway.dto.response.DefinitionSummaryResponse;
import com.mcpgateway.mapper.DefinitionMapper;
import com.mcpgateway.repository.AiModelRepository;
import com.mcpgateway.repository.DefinitionRepository;
import com.mcpgateway.repository.HostGroupRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.SecurityUtils;
import com.mcpgateway.service.intf.AuditService;
import com.mcpgateway.service.intf.DefinitionService;
import com.mcpgateway.event.CatalogueChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Definition lifecycle.
 *
 * <p>Actions and inputs are replaced as a whole on update. The panel edits the entire
 * document and sends it back, so diffing would add a protocol both sides would have to
 * agree on for no gain.
 */
@Service
@RequiredArgsConstructor
public class DefinitionServiceImpl implements DefinitionService {

    private static final String RESOURCE = "Definition";

    private final DefinitionRepository definitionRepository;
    private final AiModelRepository aiModelRepository;
    private final HostGroupRepository hostGroupRepository;
    private final UserRepository userRepository;
    private final DefinitionMapper mapper;
    private final AuditService auditService;
    private final SecretRepository secretRepository;
    private final CipherClient cipherClient;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DefinitionSummaryResponse> findAll(Pageable pageable) {
        return PageResponse.from(definitionRepository.findAllBy(pageable), mapper::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public DefinitionResponse findById(Long id) {
        return mapper.toResponse(requireDefinitionWithActions(id));
    }

    @Override
    @Transactional
    public DefinitionResponse create(DefinitionRequest request) {
        assertNamesFree(request, null);

        Definition definition = Definition.builder()
                .name(request.name())
                .toolName(request.toolName())
                .toolDescription(request.toolDescription())
                .model(requireModel(request.modelId()))
                .systemPrompt(request.systemPrompt())
                .inputs(toInputs(request.inputs()))
                .enabled(request.enabled() == null || request.enabled())
                .createdBy(currentUser())
                .updatedBy(currentUser())
                .build();

        applyActions(definition, request.actions());

        Definition saved = definitionRepository.save(definition);
        auditService.record("definition.created", "definition", saved.getId(),
                Map.of("toolName", saved.getToolName()));
        eventPublisher.publishEvent(new CatalogueChangedEvent("definition.created"));

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DefinitionResponse update(Long id, DefinitionRequest request) {
        Definition definition = requireDefinitionWithActions(id);
        assertNamesFree(request, id);

        definition.setName(request.name());
        definition.setToolName(request.toolName());
        definition.setToolDescription(request.toolDescription());
        definition.setModel(requireModel(request.modelId()));
        definition.setSystemPrompt(request.systemPrompt());
        definition.setInputs(toInputs(request.inputs()));
        definition.setUpdatedBy(currentUser());

        if (request.enabled() != null) {
            definition.setEnabled(request.enabled());
        }

        // orphanRemoval deletes the previous rows once they leave the collection.
        definition.clearActions();
        applyActions(definition, request.actions());

        auditService.record("definition.updated", "definition", id,
                Map.of("actionCount", definition.getActions().size()));
        eventPublisher.publishEvent(new CatalogueChangedEvent("definition.updated"));

        return mapper.toResponse(definition);
    }

    @Override
    @Transactional
    public DefinitionResponse toggleEnabled(Long id) {
        Definition definition = requireDefinitionWithActions(id);
        definition.setEnabled(!definition.isEnabled());
        definition.setUpdatedBy(currentUser());

        auditService.record("definition.toggled", "definition", id,
                Map.of("enabled", definition.isEnabled()));
        eventPublisher.publishEvent(new CatalogueChangedEvent("definition.toggled"));

        return mapper.toResponse(definition);
    }

    @Override
    @Transactional
    public DefinitionResponse duplicate(Long id) {
        Definition source = requireDefinitionWithActions(id);

        Definition copy = Definition.builder()
                .name(freeName(source.getName()))
                .toolName(freeToolName(source.getToolName()))
                .toolDescription(source.getToolDescription())
                .model(source.getModel())
                .systemPrompt(source.getSystemPrompt())
                .inputs(source.getInputs().stream()
                        .map(com.mcpgateway.domain.json.DefinitionInput::copy)
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new)))
                .enabled(false)
                .createdBy(currentUser())
                .updatedBy(currentUser())
                .build();

        source.getActions().forEach(action -> copy.addAction(Action.builder()
                .kind(action.getKind())
                .name(action.getName())
                .description(action.getDescription())
                // Copy, otherwise the duplicate and its source would share one document.
                .config(action.getConfig().copy())
                .hostGroup(action.getHostGroup())
                .build()));

        Definition saved = definitionRepository.save(copy);
        auditService.record("definition.duplicated", "definition", saved.getId(),
                Map.of("sourceId", id));
        // No catalogue event: a duplicate is created disabled, so the MCP server's
        // catalogue is unchanged until someone enables it, which raises its own event.

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Definition definition = definitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));

        List<Long> secretIds = definition.getActions().stream()
                .map(Action::getConfig)
                .flatMap(config -> java.util.stream.Stream.of(
                        config.getPrivateKeySecretId(),
                        config.getPassphraseSecretId(),
                        config.getPasswordSecretId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        definitionRepository.delete(definition);

        // After the definition, and only then: nothing else points at these, and leaving
        // them behind would accumulate rows nothing can reach and nobody would think to
        // look for. Distinct, because one secret may be shared by two actions.
        if (!secretIds.isEmpty()) {
            definitionRepository.flush();
            secretRepository.deleteAllById(secretIds);
        }

        auditService.record("definition.deleted", "definition", id,
                Map.of("toolName", definition.getToolName()));
        eventPublisher.publishEvent(new CatalogueChangedEvent("definition.deleted"));
    }

    /* ------------------------------- helpers ------------------------------- */

    private Definition requireDefinitionWithActions(Long id) {
        return definitionRepository.findWithActionsById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    private AiModel requireModel(Long modelId) {
        return aiModelRepository.findById(modelId)
                .orElseThrow(() -> new ResourceNotFoundException("AI model", modelId));
    }

    private com.mcpgateway.domain.entity.User currentUser() {
        return SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null);
    }

    /** Both the display name and the tool name are unique; report which one clashed. */
    private void assertNamesFree(DefinitionRequest request, Long excludedId) {
        boolean nameTaken = excludedId == null
                ? definitionRepository.existsByName(request.name())
                : definitionRepository.existsByNameAndIdNot(request.name(), excludedId);

        if (nameTaken) {
            throw new ConflictException("A definition named '%s' already exists".formatted(request.name()));
        }

        boolean toolTaken = excludedId == null
                ? definitionRepository.existsByToolName(request.toolName())
                : definitionRepository.existsByToolNameAndIdNot(request.toolName(), excludedId);

        if (toolTaken) {
            throw new ConflictException("Tool name '%s' is already published".formatted(request.toolName()));
        }
    }

    private List<DefinitionInput> toInputs(List<DefinitionInputRequest> requests) {
        if (requests == null) {
            return new ArrayList<>();
        }
        return requests.stream()
                .map(input -> DefinitionInput.builder()
                        .key(input.key())
                        .label(input.label() == null ? "" : input.label())
                        .type(input.type())
                        .required(input.required())
                        // A password input never carries a stored default.
                        .defaultValue(input.type() == com.mcpgateway.domain.json.InputType.PASSWORD
                                ? "" : nullToEmpty(input.defaultValue()))
                        .placeholder(nullToEmpty(input.placeholder()))
                        .options(input.options() == null ? new ArrayList<>() : new ArrayList<>(input.options()))
                        .source(input.sourceOrDefault())
                        .build())
                .toList();
    }

    private void applyActions(Definition definition, List<ActionRequest> requests) {
        if (requests == null) {
            return;
        }
        for (ActionRequest request : requests) {
            // Copy, so a caller cannot hold a reference into the persisted state.
            ActionConfig config = request.config().copy();
            applyCredentials(definition, config, request);

            definition.addAction(Action.builder()
                    .kind(request.kind())
                    .name(request.name())
                    .description(nullToEmpty(request.description()))
                    .config(config)
                    .hostGroup(resolveHostGroup(request))
                    .build());
        }
    }

    /**
     * Stores any newly supplied SSH credential and points the config at it.
     *
     * <p>The plaintext never reaches the config document. It is sealed by mcp-cipher and
     * kept in {@code secrets}; what the document holds is an id, which is worth nothing on
     * its own. Until this existed the panel had a field for a private key that was silently
     * discarded — the config class has no member for one, so Jackson dropped it and the
     * save reported success.
     */
    private void applyCredentials(Definition definition, ActionConfig config, ActionRequest request) {
        config.setPrivateKeySecretId(storeCredential(
                definition, config.getPrivateKeySecretId(), request.privateKey(),
                SecretKind.SSH_PRIVATE_KEY, "ssh-private-key"));

        config.setPassphraseSecretId(storeCredential(
                definition, config.getPassphraseSecretId(), request.passphrase(),
                SecretKind.SSH_PASSPHRASE, "ssh-passphrase"));

        // Named by the kind of action it belongs to. A database password filed under
        // "ssh-password" is readable but wrong, and the name is the only thing anyone
        // looking at the secrets table has to go on.
        config.setPasswordSecretId(storeCredential(
                definition, config.getPasswordSecretId(), request.password(),
                SecretKind.PASSWORD,
                request.kind() == ActionKind.DB ? "db-password" : "ssh-password"));
    }

    /**
     * Seals one credential, or leaves the existing binding alone.
     *
     * @param existingId the secret the config already points at, if any
     * @param plaintext  null keeps it, blank removes it, anything else replaces it
     * @return the id the config should now hold
     */
    private Long storeCredential(Definition definition, Long existingId, String plaintext,
                                 SecretKind kind, String role) {
        if (plaintext == null) {
            return existingId;
        }

        if (plaintext.isBlank()) {
            // Unbound rather than deleted here: the row may still be referenced by the
            // action this update is replacing, and it is cleaned up when the definition is.
            return null;
        }

        CipherClient.Sealed sealed = cipherClient.encrypt(plaintext, kind.wireValue());

        Secret secret = existingId == null
                ? null
                : secretRepository.findById(existingId).orElse(null);

        if (secret == null) {
            secret = Secret.builder()
                    .name(freeSecretName(role + "/" + definition.getToolName()))
                    .kind(kind)
                    .createdBy(currentUser())
                    .build();
        }

        secret.setCiphertext(sealed.ciphertext());
        // Stored rather than assumed: mcp-cipher can rotate its active key, and a value has
        // to say which key opens it or it becomes unreadable the moment one is retired.
        secret.setKeyId(sealed.keyId());

        return secretRepository.save(secret).getId();
    }

    /**
     * A unique, readable name for a stored credential.
     *
     * <p>The column is unique and a tool name is not, so a counter is appended rather than
     * letting the save fail on a constraint the user cannot see.
     */
    private String freeSecretName(String base) {
        String candidate = base;
        int suffix = 2;
        while (secretRepository.existsByName(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    /** A host group is only meaningful for an SSH action, so other kinds ignore it. */
    private HostGroup resolveHostGroup(ActionRequest request) {
        if (request.hostGroupId() == null || request.kind() != ActionKind.SSH) {
            return null;
        }
        return hostGroupRepository.findById(request.hostGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Host group", request.hostGroupId()));
    }

    private String freeName(String base) {
        String candidate = base + " (copy)";
        int suffix = 2;

        while (definitionRepository.existsByName(candidate)) {
            candidate = "%s (copy %d)".formatted(base, suffix++);
        }
        return candidate;
    }

    private String freeToolName(String base) {
        String candidate = base + "_copy";
        int suffix = 2;

        while (definitionRepository.existsByToolName(candidate)) {
            candidate = "%s_copy_%d".formatted(base, suffix++);
        }
        return candidate;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
