package com.mcpgateway.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Mirrors the PostgreSQL enum type {@code log_level}.
 * Database labels are lower case; the JSON contract uses the same lower case form.
 */
public enum LogLevel {
    INFO,
    WARN,
    ERROR;

    /** Lower case form used both in the database and in the API payloads. */
    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static LogLevel fromWireValue(String value) {
        return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
