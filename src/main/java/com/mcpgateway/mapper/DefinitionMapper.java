package com.mcpgateway.mapper;

import com.mcpgateway.domain.entity.Action;
import com.mcpgateway.domain.entity.Definition;
import com.mcpgateway.dto.response.ActionResponse;
import com.mcpgateway.dto.response.DefinitionResponse;
import com.mcpgateway.dto.response.DefinitionSummaryResponse;
import com.mcpgateway.dto.response.SealedSecretResponse;
import com.mcpgateway.domain.json.ActionConfig;
import com.mcpgateway.repository.SecretRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Entity to response translation for definitions and their actions. */
@Component
@RequiredArgsConstructor
public class DefinitionMapper {

    private final SecretRepository secretRepository;

    public DefinitionResponse toResponse(Definition definition) {
        return new DefinitionResponse(
                definition.getId(),
                definition.getName(),
                definition.getToolName(),
                definition.getToolDescription(),
                definition.getModel() == null ? null : definition.getModel().getId(),
                definition.getModel() == null ? null : definition.getModel().getName(),
                definition.getModel() == null ? null : definition.getModel().getModelId(),
                definition.getModel() == null ? null : definition.getModel().getProvider(),
                definition.getModel() == null ? "" : definition.getModel().getEndpoint(),
                sealedApiKeyOf(definition),
                definition.getModel() == null ? null : definition.getModel().getParams(),
                definition.getSystemPrompt(),
                definition.getInputs(),
                definition.getActions().stream().map(this::toActionResponse).toList(),
                definition.isEnabled(),
                definition.getUpdatedAt());
    }

    public DefinitionSummaryResponse toSummary(Definition definition) {
        Map<String, Long> countsByKind = definition.getActions().stream()
                .collect(Collectors.groupingBy(
                        action -> action.getKind().wireValue(), Collectors.counting()));

        return new DefinitionSummaryResponse(
                definition.getId(),
                definition.getName(),
                definition.getToolName(),
                definition.getToolDescription(),
                definition.getModel() == null ? null : definition.getModel().getModelId(),
                countsByKind,
                definition.getInputs().size(),
                definition.isEnabled(),
                definition.getUpdatedAt());
    }

    public ActionResponse toActionResponse(Action action) {
        return new ActionResponse(
                action.getId(),
                action.getKind(),
                action.getName(),
                action.getDescription(),
                action.getPosition(),
                action.getConfig(),
                action.getHostGroup() == null ? null : action.getHostGroup().getId(),
                action.getHostGroup() == null ? null : action.getHostGroup().getName(),
                // Target resolution is domain knowledge, so the entity owns it.
                action.resolveTargetCount(),
                hostsOf(action),
                action.getConfig().getHostKeys(),
                credentialsOf(action));
    }

    /**
     * The model's API key as stored, or null when it has none.
     *
     * <p>Ciphertext: this service cannot read it either, so passing it on costs nothing.
     * What it buys is that a key entered once in the panel is the key the planner uses,
     * rather than a second copy living in that process's environment.
     */
    private SealedSecretResponse sealedApiKeyOf(Definition definition) {
        if (definition.getModel() == null || definition.getModel().getApiKeySecret() == null) {
            return null;
        }

        var secret = definition.getModel().getApiKeySecret();
        return new SealedSecretResponse(
                secret.getCiphertext(), secret.getKeyId(), secret.getKind().wireValue());
    }

    /**
     * Every host this action would reach, with the group expanded.
     *
     * <p>The count alone was enough while the planner only described what would happen.
     * An executor needs the names, and this service is the only one that holds them.
     */
    private List<String> hostsOf(Action action) {
        ActionConfig config = action.getConfig();

        return switch (String.valueOf(config.getTargetMode())) {
            case "group" -> action.getHostGroup() == null
                    ? List.of()
                    : List.copyOf(action.getHostGroup().getHosts());
            case "list" -> List.copyOf(config.getHosts());
            case "single" -> config.getHost() == null || config.getHost().isBlank()
                    ? List.of()
                    : List.of(config.getHost());
            default -> List.of();
        };
    }

    /**
     * The action's secrets, still sealed.
     *
     * <p>Keyed by role rather than by secret name, so a consumer asks for "the private key"
     * without knowing what it was called here. The values are ciphertext: this service
     * cannot read them either, and passing them on is not a disclosure.
     */
    private Map<String, SealedSecretResponse> credentialsOf(Action action) {
        ActionConfig config = action.getConfig();

        Map<String, SealedSecretResponse> credentials = new LinkedHashMap<>();
        putSealed(credentials, "privateKey", config.getPrivateKeySecretId());
        putSealed(credentials, "passphrase", config.getPassphraseSecretId());
        putSealed(credentials, "password", config.getPasswordSecretId());

        return credentials;
    }

    private void putSealed(Map<String, SealedSecretResponse> into, String role, Long secretId) {
        if (secretId == null) {
            return;
        }

        secretRepository.findById(secretId).ifPresent(secret -> into.put(role,
                new SealedSecretResponse(secret.getCiphertext(), secret.getKeyId(),
                        secret.getKind().wireValue())));
    }
}
