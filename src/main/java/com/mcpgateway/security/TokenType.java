package com.mcpgateway.security;

import java.util.Locale;

/** Distinguishes the two token flavours; carried as a claim and as a Redis key prefix. */
public enum TokenType {

    ACCESS,
    REFRESH;

    public String claimValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
