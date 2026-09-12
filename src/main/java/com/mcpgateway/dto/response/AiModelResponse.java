package com.mcpgateway.dto.response;

import com.mcpgateway.domain.enums.ModelProvider;
import com.mcpgateway.domain.json.ModelHealth;
import com.mcpgateway.domain.json.ModelParams;

import java.time.Instant;

/**
 * An AI model connection.
 *
 * <p>The API key is represented by the id and name of the secret that holds it. The
 * gateway never sees the plaintext, so it cannot produce a {@code sk-ant-…a71b} style
 * preview; showing a fixed row of dots would only pretend otherwise. The secret's name
 * is meaningful, safe to display and enough for the panel to say which key is bound.
 */
public record AiModelResponse(Long id,
                              String name,
                              ModelProvider provider,
                              String modelId,
                              String endpoint,
                              Long apiKeySecretId,
                              String apiKeySecretName,
                              /*
                               * Whether a key is stored. The key itself is never returned
                               * — a form only needs to know if there is one, to say so
                               * instead of looking identical to a model with none.
                               */
                              boolean hasApiKey,
                              ModelParams params,
                              ModelHealth health,
                              boolean enabled,
                              String notes,
                              long definitionCount,
                              Instant updatedAt) {
}
