package com.mcpgateway.property;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Invitation lifetime, bound from {@code mcp.invitation.*}.
 *
 * <p>How long an invite link stays valid is a policy decision that differs per
 * environment, so it belongs in configuration rather than in the service.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mcp.invitation")
public class InvitationProperties {

    @NotNull
    private Duration ttl = Duration.ofDays(7);
}
