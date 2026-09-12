package com.mcpgateway.property;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Signing material and lifetimes for issued tokens.
 *
 * <p>Bound from {@code mcp.jwt.*} in {@code application.yml}. {@code @Validated} makes
 * a missing or blank secret fail at start up rather than at the first login attempt.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mcp.jwt")
public class JwtProperties {

    /** HMAC secret; must be at least 32 bytes for HS256. */
    @NotBlank
    private String secret;

    @NotBlank
    private String issuer = "mcp-gateway";

    @NotNull
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    @NotNull
    private Duration refreshTokenTtl = Duration.ofDays(7);
}
