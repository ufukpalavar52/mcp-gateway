package com.mcpgateway.property;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Where the MCP server lives and how to reach it.
 *
 * <p>Bound from {@code mcp.server.*}. The MCP server holds no database and no gateway
 * credentials: this service pushes it the catalogue and asks it for decisions, so the
 * connection is one way and the shared secret travels with the request.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mcp.server")
public class McpServerProperties {

    @NotBlank
    private String url = "http://localhost:8000";

    /** Presented as {@code X-MCP-Token}. Empty means the MCP server has no check. */
    private String token = "";

    private Duration timeout = Duration.ofSeconds(30);

    /** Publish the catalogue on start up and after every definition change. */
    private boolean autoPublish = true;

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
