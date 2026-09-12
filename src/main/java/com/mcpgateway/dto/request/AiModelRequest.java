package com.mcpgateway.dto.request;

import com.mcpgateway.domain.enums.ModelProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create or update payload for an AI model connection. */
public record AiModelRequest(

        @NotBlank(message = "Display name is required")
        @Size(max = 160, message = "Display name must be at most 160 characters")
        String name,

        @NotNull(message = "Provider is required")
        ModelProvider provider,

        @NotBlank(message = "Model id is required")
        String modelId,

        @NotBlank(message = "Endpoint is required")
        String endpoint,

        /** Reference to an already stored secret, for binding a key that exists. */
        Long apiKeySecretId,

        /**
         * A new key, in plaintext, to be stored.
         *
         * Write only: it is encrypted into {@code secrets} and never appears in any
         * response. Leaving it null keeps whatever key the model already had, which is
         * what makes it safe to save the form again without retyping the key; sending an
         * empty string unbinds it.
         */
        @Size(max = 4096, message = "API key must be at most 4096 characters")
        String apiKey,

        @Valid
        ModelParamsRequest params,

        Boolean enabled,

        String notes) {
}
