package com.mcpgateway.property;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Origins allowed to call the API.
 *
 * <p>Bound from {@code mcp.cors.*}. The panel runs on a different port in development,
 * so the list is environment specific and never hard coded.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mcp.cors")
public class CorsProperties {

    @NotEmpty
    private List<String> allowedOrigins = List.of("http://localhost:3000");

    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    private boolean allowCredentials = true;

    private long maxAgeSeconds = 3600L;
}
