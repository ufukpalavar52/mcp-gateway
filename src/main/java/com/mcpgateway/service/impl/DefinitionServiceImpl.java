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
import com.mcpgateway.domain.entity.DefinitionPermission;
import com.mcpgateway.dto.request.DefinitionAccessRequest;
import com.mcpgateway.dto.response.DefinitionAccessResponse;
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
import java.util.Set;

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
    private final com.mcpgateway.service.DefinitionAccessGuard access;
    private final com.mcpgateway.repository.DefinitionPermissionRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DefinitionSummaryResponse> findAll(Pageable pageable) {
        // An administrator sees everything, so the page comes straight from the database.
        if (access.unrestricted()) {
            return PageResponse.from(definitionRepository.findAllBy(pageable), mapper::toSummary);
        }

        // For everybody else the permitted set is worked out first and paged afterwards.
        //
        // Filtering the page the database returned would be wrong rather than merely
        // inelegant: page two of a list whose first page lost four rows is not page two of
        // anything, and the total would count definitions the reader cannot see. An
        // installation holds tens of definitions, so paging them in memory is honest and
        // cheap; teaching the query about permissions is the answer when it is thousands.
        List<Definition> allowed = access.runnable(definitionRepository.findAll());

        int from = (int) Math.min(pageable.getOffset(), allowed.size());
        int to = Math.min(from + pageable.getPageSize(), allowed.size());
        int pages = (int) Math.ceil((double) allowed.size() / pageable.getPageSize());

        return new PageResponse<>(
                allowed.subList(from, to).stream().map(mapper::toSummary).toList(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                allowed.size(),
                pages,
                to >= allowed.size());
    }

    @Override
    @Transactional(readOnly = true)
    public DefinitionResponse findById(Long id) {
        Definition definition = requireDefinitionWithActions(id);

        // Not found rather than forbidden: to somebody with no access, a definition they
        // may not reach and one that is not there are the same fact.
        if (!access.mayRun(definition)) {
            throw new ResourceNotFoundException(RESOURCE, id);
        }

        return mapper.toResponse(definition);
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
        Definition definition = requireEditable(id);
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
        Definition definition = requireEditable(id);
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
        Definition source = requireEditable(id);

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
        Definition definition = requireEditable(id);

        // What this definition points at, minus whatever anything else still points at.
        //
        // The comment that used to sit below said "nothing else points at these", and it
        // counted only the sharing *inside* this definition. A secret has no owner — it is
        // referenced by id from inside an action's JSON — and one SSH key is quite properly
        // shared by every definition that reaches the same host. Three of them shared one
        // here. Deleting any of the three took the key with it and the other two broke at
        // the next run, saying the key was missing, with nothing to connect that to a
        // deletion somebody had made days earlier.
        //
        // Sharing was never the mistake. The deletion was.
        Set<Long> mine = secretsOf(definition);
        mine.removeAll(stillWanted(id));

        definitionRepository.delete(definition);

        // After the definition, and only then: leaving these behind would accumulate rows
        // nothing can reach and nobody would think to look for.
        if (!mine.isEmpty()) {
            definitionRepository.flush();
            secretRepository.deleteAllById(mine);
        }

        auditService.record("definition.deleted", "definition", id,
                Map.of("toolName", definition.getToolName()));
        eventPublisher.publishEvent(new CatalogueChangedEvent("definition.deleted"));
    }

    /**
     * The definition, if this caller may change it.
     *
     * <p>Not found rather than forbidden, exactly as reading one is: somebody who cannot
     * see a definition should not learn it exists by being refused permission to edit it.
     */
    private Definition requireEditable(Long id) {
        Definition definition = requireDefinitionWithActions(id);

        if (!access.mayEdit(definition)) {
            throw new ResourceNotFoundException(RESOURCE, id);
        }

        return definition;
    }

    /** Every secret this definition's actions reference, by id. */
    private static Set<Long> secretsOf(Definition definition) {
        return definition.getActions().stream()
                .map(Action::getConfig)
                .flatMap(config -> java.util.stream.Stream.of(
                        config.getPrivateKeySecretId(),
                        config.getPassphraseSecretId(),
                        config.getPasswordSecretId()))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
    }

    /**
     * Secrets some other definition is still using.
     *
     * <p>Read across every other definition rather than queried out of the JSON: the
     * references live inside an action's JSONB and there is no foreign key to ask. An
     * installation holds tens of definitions, not thousands, so the honest loop is cheaper
     * than the query that would avoid it — and far easier to be sure of.
     */
    private Set<Long> stillWanted(Long excluding) {
        return definitionRepository.findAll().stream()
                .filter(other -> !other.getId().equals(excluding))
                .flatMap(other -> secretsOf(other).stream())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public DefinitionAccessResponse accessOf(Long id) {
        Definition definition = requireEditable(id);

        return new DefinitionAccessResponse(
                definition.getAccess(),
                permissionRepository.findByDefinitionId(id).stream()
                        .map(row -> new DefinitionAccessResponse.Grant(
                                row.getUser().getId(),
                                row.getUser().getEmail(),
                                row.getUser().getFullName(),
                                row.isCanRun(),
                                row.isCanEdit()))
                        .toList());
    }

    @Override
    @Transactional
    public DefinitionAccessResponse replaceAccess(Long id, DefinitionAccessRequest request) {
        Definition definition = requireEditable(id);

        definition.setAccess(request.access());

        // Replaced wholesale, like the actions: the screen edits the whole list and a
        // partial update would need a diffing protocol the client does not speak.
        permissionRepository.deleteAll(permissionRepository.findByDefinitionId(id));

        for (DefinitionAccessRequest.Grant grant : request.permissions() == null
                ? List.<DefinitionAccessRequest.Grant>of() : request.permissions()) {

            // A row that grants nothing is not a restriction, it is a line nobody can read.
            // Somebody meaning "take their access away" removes them from the list.
            if (!grant.canRun() && !grant.canEdit()) {
                continue;
            }

            permissionRepository.save(DefinitionPermission.builder()
                    .definition(definition)
                    .user(userRepository.findById(grant.userId())
                            .orElseThrow(() -> new ResourceNotFoundException("User", grant.userId())))
                    .canRun(grant.canRun())
                    .canEdit(grant.canEdit())
                    .build());
        }

        definitionRepository.save(definition);
        auditService.record("definition.access.changed", "definition", id,
                Map.of("access", request.access().name(),
                        "granted", request.permissions() == null ? 0 : request.permissions().size()));

        // The catalogue a caller sees depends on this, so it is rebuilt like any other
        // change to what a definition is.
        eventPublisher.publishEvent(new CatalogueChangedEvent("definition.access.changed"));

        return accessOf(id);
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
