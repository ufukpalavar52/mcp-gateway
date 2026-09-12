package com.mcpgateway.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Mirrors the PostgreSQL enum type {@code secret_kind}.
 * Database labels are lower case; the JSON contract uses the same lower case form.
 */
public enum SecretKind {
    SSH_PRIVATE_KEY,
    SSH_PASSPHRASE,
    PASSWORD,
    API_TOKEN,
    MODEL_API_KEY,
    OTHER;

    /** Lower case form used both in the database and in the API payloads. */
    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static SecretKind fromWireValue(String value) {
        return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
