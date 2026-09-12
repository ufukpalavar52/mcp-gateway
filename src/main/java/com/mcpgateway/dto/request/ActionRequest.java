package com.mcpgateway.dto.request;

import com.mcpgateway.domain.enums.ActionKind;
import com.mcpgateway.domain.json.ActionConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One action of a definition.
 *
 * <p>{@code config} is intentionally loose at the field level: which of its members
 * are required depends on {@code kind}, {@code commandMode} and {@code queryMode}.
 * Those cross-field rules live in the action rule validator.
 */
public record ActionRequest(

        Long id,

        @NotNull(message = "Action kind is required")
        ActionKind kind,

        @NotBlank(message = "Action name is required")
        String name,

        String description,

        @NotNull(message = "Action config is required")
        ActionConfig config,

        /** Required when the SSH target mode is {@code group}. */
        Long hostGroupId,

        /*
         * Credentials in plaintext, on their way to being stored.
         *
         * Write only: each is sealed through mcp-cipher, kept in `secrets`, and referenced
         * from the config by id. None of them appears in any response, and none is ever
         * written to the config document — which is why the document has no field for them.
         *
         * Three-valued, as with a model's API key. Null keeps whatever is already stored,
         * so a form can be saved again without retyping a credential it was never given;
         * an empty string unbinds it; anything else replaces it.
         */
        @Size(max = 16384, message = "Private key must be at most 16384 characters")
        String privateKey,

        @Size(max = 1024, message = "Passphrase must be at most 1024 characters")
        String passphrase,

        @Size(max = 1024, message = "Password must be at most 1024 characters")
        String password) {
}
