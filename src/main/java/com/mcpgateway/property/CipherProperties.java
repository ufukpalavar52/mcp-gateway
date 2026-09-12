package com.mcpgateway.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How to reach mcp-cipher.
 *
 * <p>No key here, and none anywhere in this service. That is the point of a separate
 * cipher: this process stores ciphertext and a key id, and cannot read a secret without
 * asking — so a dump of this database, or of this configuration, reveals nothing.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "mcp.cipher")
public class CipherProperties {

    /** host:port of the gRPC service. */
    private String address = "127.0.0.1:9090";

    /** Presented as {@code x-cipher-token}. Empty means the service has no check. */
    private String token = "";

    private Duration timeout = Duration.ofSeconds(5);

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
