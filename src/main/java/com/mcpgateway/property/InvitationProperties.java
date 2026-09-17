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

    /**
     * Where the panel lives, so the link in the mail points at a page somebody can open.
     *
     * <p>Not this service's own address: what is sent is a page, not an endpoint, and the
     * gateway has no way of knowing where the panel is served from.
     */
    private String panelUrl = "http://localhost:3000";

    /** Who the invitation appears to come from. */
    private String from = "";
}
