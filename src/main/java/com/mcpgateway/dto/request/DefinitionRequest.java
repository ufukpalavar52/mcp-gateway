package com.mcpgateway.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create or update payload for a definition, which is one MCP tool.
 *
 * <p>Actions and inputs are replaced wholesale rather than patched: the panel edits
 * the whole document, and partial updates would need a diffing protocol the client
 * does not speak.
 */
public record DefinitionRequest(

        @NotBlank(message = "Definition name is required")
        @Size(max = 160, message = "Definition name must be at most 160 characters")
        String name,

        @NotBlank(message = "Tool name is required")
        String toolName,

        @NotBlank(message = "Tool description is required, the calling model reads it")
        String toolDescription,

        @NotNull(message = "An AI model must be selected")
        Long modelId,

        @NotBlank(message = "System prompt cannot be empty")
        String systemPrompt,

        /*
         * @Valid sits on the type argument, not on the list. Placing it on the container
         * is deprecated in Bean Validation 3 and Hibernate Validator logs HV000271 for
         * every request; the element form is what actually expresses "validate each item".
         */
        List<@Valid DefinitionInputRequest> inputs,

        @NotEmpty(message = "At least one action is required")
        List<@Valid ActionRequest> actions,

        Boolean enabled) {
}
