package com.mcpgateway.domain.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Type of a dynamic input. Unlike the column enums this one lives inside JSONB,
 * so it has no PostgreSQL counterpart and is validated by the application only.
 */
public enum InputType {

    TEXT,
    PASSWORD,
    NUMBER,
    TEXTAREA,

    /**
     * A body of text destined for a quoted heredoc: a file's contents, a config, a script.
     *
     * <p>The one type exempt, in the MCP server, from the shell-metacharacter scan every
     * other substituted value gets — a file made of newlines cannot pass that scan and
     * should not have to. What replaces it is narrower: the command must place the value in
     * a <em>quoted</em> heredoc, where the shell expands nothing, and the value may not
     * contain the terminator that would close it early.
     *
     * <p>{@link #TEXTAREA} is not this. It means a bigger box and nothing more, and it
     * keeps the ordinary scan.
     */
    BLOCK,

    SELECT,
    BOOLEAN,
    DATE;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static InputType fromWireValue(String value) {
        return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
    }

    /** Maps the input type onto its JSON Schema primitive. */
    public String jsonSchemaType() {
        return switch (this) {
            case NUMBER -> "number";
            case BOOLEAN -> "boolean";
            default -> "string";
        };
    }

    /** JSON Schema {@code format} hint, or {@code null} when the type needs none. */
    public String jsonSchemaFormat() {
        return switch (this) {
            case DATE -> "date";
            case PASSWORD -> "password";
            default -> null;
        };
    }
}
