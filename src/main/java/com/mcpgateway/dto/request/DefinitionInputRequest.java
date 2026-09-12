package com.mcpgateway.dto.request;

import com.mcpgateway.domain.json.InputSource;
import com.mcpgateway.domain.json.InputType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * One dynamic input of a definition.
 *
 * <p>Key format and uniqueness are enforced by the business rule validator, because
 * uniqueness is a property of the whole list rather than of a single element.
 */
public record DefinitionInputRequest(

        @NotBlank(message = "Input key is required")
        String key,

        String label,

        @NotNull(message = "Input type is required")
        InputType type,

        boolean required,

        String defaultValue,

        String placeholder,

        List<String> options,

        /**
         * Who may decide this value. See {@link InputSource}.
         *
         * <p>Null means {@code PROMPT}: an older client that does not send the field keeps
         * the behaviour every input had before it existed.
         */
        InputSource source) {

    public InputSource sourceOrDefault() {
        return source == null ? InputSource.PROMPT : source;
    }
}
