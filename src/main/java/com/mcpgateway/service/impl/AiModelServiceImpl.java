package com.mcpgateway.service.impl;

import com.mcpgateway.common.exception.BusinessRuleException;
import com.mcpgateway.common.exception.ConflictException;
import com.mcpgateway.common.exception.ResourceNotFoundException;
import com.mcpgateway.domain.entity.AiModel;
import com.mcpgateway.domain.entity.Secret;
import com.mcpgateway.domain.json.ModelParams;
import com.mcpgateway.dto.request.AiModelRequest;
import com.mcpgateway.dto.request.ModelParamsRequest;
import com.mcpgateway.dto.response.AiModelResponse;
import com.mcpgateway.mapper.AiModelMapper;
import com.mcpgateway.repository.AiModelRepository;
import com.mcpgateway.repository.DefinitionRepository;
import com.mcpgateway.client.CipherClient;
import com.mcpgateway.domain.enums.SecretKind;
import com.mcpgateway.repository.SecretRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.SecurityUtils;
import com.mcpgateway.service.intf.AiModelService;
import com.mcpgateway.service.intf.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** CRUD for model connections, plus the referential checks the schema cannot express. */
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    private static final String RESOURCE = "AI model";

    private final AiModelRepository aiModelRepository;
    private final DefinitionRepository definitionRepository;
    private final SecretRepository secretRepository;
    private final CipherClient cipherClient;
    private final UserRepository userRepository;
    private final AiModelMapper mapper;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<AiModelResponse> findAll() {
        return aiModelRepository.findAll().stream()
                .map(model -> mapper.toResponse(model, definitionRepository.countByModelId(model.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AiModelResponse findById(Long id) {
        AiModel model = requireModel(id);
        return mapper.toResponse(model, definitionRepository.countByModelId(id));
    }

    @Override
    @Transactional
    public AiModelResponse create(AiModelRequest request) {
        if (aiModelRepository.existsByName(request.name())) {
            throw new ConflictException("A model named '%s' already exists".formatted(request.name()));
        }

        AiModel model = AiModel.builder()
                .name(request.name())
                .provider(request.provider())
                .modelId(request.modelId())
                .endpoint(request.endpoint())
                .apiKeySecret(resolveSecret(request.apiKeySecretId()))
                .params(toParams(request.params()))
                .enabled(request.enabled() == null || request.enabled())
                .notes(request.notes() == null ? "" : request.notes())
                .createdBy(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .build();

        applyApiKey(model, request);

        AiModel saved = aiModelRepository.save(model);
        auditService.record("model.created", "ai_model", saved.getId(),
                Map.of("modelId", saved.getModelId()));

        return mapper.toResponse(saved, 0L);
    }

    @Override
    @Transactional
    public AiModelResponse update(Long id, AiModelRequest request) {
        AiModel model = requireModel(id);

        if (aiModelRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("A model named '%s' already exists".formatted(request.name()));
        }

        model.setName(request.name());
        model.setProvider(request.provider());
        model.setModelId(request.modelId());
        model.setEndpoint(request.endpoint());
        if (request.apiKeySecretId() != null) {
            model.setApiKeySecret(resolveSecret(request.apiKeySecretId()));
        }
        applyApiKey(model, request);
        model.setParams(toParams(request.params()));
        model.setNotes(request.notes() == null ? "" : request.notes());

        if (request.enabled() != null) {
            model.setEnabled(request.enabled());
        }

        auditService.record("model.updated", "ai_model", id, Map.of());
        return mapper.toResponse(model, definitionRepository.countByModelId(id));
    }

    @Override
    @Transactional
    public AiModelResponse toggleEnabled(Long id) {
        AiModel model = requireModel(id);
        model.setEnabled(!model.isEnabled());

        auditService.record("model.toggled", "ai_model", id, Map.of("enabled", model.isEnabled()));
        return mapper.toResponse(model, definitionRepository.countByModelId(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AiModel model = requireModel(id);
        long usage = definitionRepository.countByModelId(id);

        // The schema uses ON DELETE RESTRICT; failing here gives a usable message
        // instead of a raw constraint violation.
        if (usage > 0) {
            throw new BusinessRuleException(
                    "Model is used by %d definition(s); move them to another model first".formatted(usage));
        }

        Secret storedKey = model.getApiKeySecret();

        aiModelRepository.delete(model);

        // After the model, not before: the foreign key is ON DELETE RESTRICT, so the
        // secret cannot go while anything still points at it. Deleting it at all is the
        // point — a key created for this model has no other owner, and leaving it behind
        // would accumulate rows nothing can reach and nobody would think to look for.
        if (storedKey != null) {
            aiModelRepository.flush();
            secretRepository.delete(storedKey);
        }

        auditService.record("model.deleted", "ai_model", id, Map.of("name", model.getName()));
    }

    private AiModel requireModel(Long id) {
        return aiModelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    /**
     * Stores a newly supplied API key, or leaves the existing one alone.
     *
     * <p>Three cases, and the distinction between the last two is the point:
     * <ul>
     *   <li>{@code null} — no key was submitted. The model keeps whatever it had, which is
     *       what lets the form be saved again without retyping a key it never received.
     *   <li>blank — the key is being removed deliberately.
     *   <li>anything else — encrypted and stored.
     * </ul>
     *
     * <p>A model's secret is reused rather than replaced, so the foreign key from
     * {@code ai_models} stays valid and renaming the model does not orphan a row.
     */
    private void applyApiKey(AiModel model, AiModelRequest request) {
        String apiKey = request.apiKey();

        if (apiKey == null) {
            return;
        }

        if (apiKey.isBlank()) {
            model.setApiKeySecret(null);
            return;
        }

        // Sent to mcp-cipher, which holds the keys. This service never has one, so what
        // comes back — ciphertext and the id of the key that sealed it — is all it stores
        // and all a database dump would ever yield.
        // The kind is the context, so whatever reads this back names the same thing.
        CipherClient.Sealed sealed =
                cipherClient.encrypt(apiKey, SecretKind.MODEL_API_KEY.wireValue());

        Secret secret = model.getApiKeySecret();

        if (secret == null) {
            secret = Secret.builder()
                    .name(secretNameFor(request.name()))
                    .kind(SecretKind.MODEL_API_KEY)
                    .createdBy(SecurityUtils.currentUserId()
                            .flatMap(userRepository::findById).orElse(null))
                    .build();
        }

        secret.setCiphertext(sealed.ciphertext());
        // Stored rather than assumed: mcp-cipher can rotate its active key, and a value
        // has to say which key opens it or it becomes unreadable the moment one is retired.
        secret.setKeyId(sealed.keyId());
        model.setApiKeySecret(secretRepository.save(secret));
    }

    /**
     * A unique, human readable name for the stored key.
     *
     * <p>The column is unique, and two models may legitimately be called the same thing at
     * different times, so a counter is appended rather than letting the save fail on a
     * constraint the user cannot see.
     */
    private String secretNameFor(String modelName) {
        String base = "model-api-key/" + modelName.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");

        String candidate = base;
        int suffix = 2;
        while (secretRepository.existsByName(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private Secret resolveSecret(Long secretId) {
        if (secretId == null) {
            return null;
        }
        return secretRepository.findById(secretId)
                .orElseThrow(() -> new ResourceNotFoundException("Secret", secretId));
    }

    /** Null params are stored as an empty object so the column is never null. */
    private ModelParams toParams(ModelParamsRequest request) {
        if (request == null) {
            return new ModelParams();
        }
        return ModelParams.builder()
                .maxTokens(request.maxTokens())
                .effort(request.effort())
                .thinking(request.thinking())
                .temperature(request.temperature())
                .timeoutMs(request.timeoutMs())
                .build();
    }
}
