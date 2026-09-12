package com.mcpgateway.domain.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Where an input is allowed to get its value.
 *
 * <p>Separate from {@link InputType}, which says what a value looks like. This says who may
 * decide it, and conflating the two is how a model ends up inventing a host name.
 */
public enum InputSource {

    /**
     * A model may read it from what the user asked, and a caller may supply it directly.
     * The default, because it is what every input did before this existed.
     */
    PROMPT,

    /**
     * Only an explicit argument fills it. A model is not shown it and cannot invent one —
     * what you want for a host name or an account id.
     */
    CALLER,

    /**
     * Always the declared default. Left out of the published schema entirely, so no client
     * can offer it and none can override it: the only way to say that part of a command is
     * not negotiable.
     */
    FIXED;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static InputSource fromWireValue(String value) {
        return value == null || value.isBlank()
                ? PROMPT
                : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
