package com.mcpgateway.domain.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * One dynamic input of a definition, stored inside {@code definitions.inputs}.
 *
 * <p>Serves two purposes: it is the source of the {@code {{placeholder}}} values
 * used in action templates, and it is what the MCP tool's JSON Schema is built from.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DefinitionInput {

    /** Placeholder key, referenced as {@code {{key}}} in action templates. */
    @NotBlank
    private String key;

    private String label;

    /** text | password | number | textarea | block | select | boolean | date */
    @NotNull
    private InputType type;

    @Builder.Default
    private boolean required = false;

    /** Never populated for {@link InputType#PASSWORD}; secrets are not stored here. */
    @Builder.Default
    private String defaultValue = "";

    @Builder.Default
    private String placeholder = "";

    /** Only meaningful for {@link InputType#SELECT}. */
    @Builder.Default
    private List<String> options = new ArrayList<>();

    /**
     * Who may decide this value. See {@link InputSource}.
     *
     * <p>Defaults to {@code PROMPT}, which is how every input behaved before the field
     * existed — an omitted value in a stored document therefore keeps working.
     */
    @Builder.Default
    private InputSource source = InputSource.PROMPT;

    /** Independent copy, including the options list; see {@link ActionConfig#copy()}. */
    public DefinitionInput copy() {
        return DefinitionInput.builder()
                .key(key)
                .label(label)
                .type(type)
                .required(required)
                .defaultValue(defaultValue)
                .placeholder(placeholder)
                .options(options == null ? new ArrayList<>() : new ArrayList<>(options))
                // Null tolerant: @Builder.Default leaves the field unset when the object
                // was built any other way, and Jackson builds it through the no-args
                // constructor. Dropping to null here would quietly change what may fill it.
                .source(source == null ? InputSource.PROMPT : source)
                .build();
    }
}
