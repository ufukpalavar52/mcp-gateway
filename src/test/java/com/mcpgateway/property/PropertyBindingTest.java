package com.mcpgateway.property;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the settings classes bind from YAML.
 *
 * <p>What this asserts is the binding itself: that a key reaches the field it names, and
 * that a duration, a list and a boolean arrive as those types rather than as strings. The
 * values come from {@code src/test/resources/application.yml}, so a passing assertion
 * means the wiring works — it says nothing about whether production is configured
 * correctly.
 *
 * <p>It used to mean both, when production configuration lived in this repository. It now
 * lives in mcp-config, and the question of whether the served file is right is asserted
 * there, against the file that is actually served. Asserting it here as well would prove
 * only that the test fixture matches itself.
 *
 * <p>No PostgreSQL or Redis needed: the dialect is stated explicitly and the pool is
 * allowed to start without a reachable database.
 */
@SpringBootTest
class PropertyBindingTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private CorsProperties corsProperties;

    @Autowired
    private InvitationProperties invitationProperties;

    @Autowired
    private McpServerProperties mcpServerProperties;

    @Test
    void jwtSettingsBind() {
        assertThat(jwtProperties.getSecret()).isNotBlank();
        assertThat(jwtProperties.getIssuer()).isEqualTo("mcp-gateway");
        assertThat(jwtProperties.getAccessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(jwtProperties.getRefreshTokenTtl()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void corsSettingsBind() {
        assertThat(corsProperties.getAllowedOrigins()).containsExactly("http://localhost:3000");
        assertThat(corsProperties.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(corsProperties.isAllowCredentials()).isTrue();
        assertThat(corsProperties.getMaxAgeSeconds()).isEqualTo(3600L);
    }

    @Test
    void invitationTtlBinds() {
        assertThat(invitationProperties.getTtl()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void mcpServerSettingsBind() {
        assertThat(mcpServerProperties.getUrl()).isEqualTo("http://127.0.0.1:8000");
        assertThat(mcpServerProperties.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        // False in the fixture, where publishing at start up would push a catalogue at
        // whatever is listening on the developer's machine. Production sets it true.
        assertThat(mcpServerProperties.isAutoPublish()).isFalse();
    }

    @Test
    void anEmptyMcpTokenMeansNoTokenIsSent() {
        // An empty token must not be presented as a header: an empty X-MCP-Token would
        // fail a check that an absent one skips.
        assertThat(mcpServerProperties.getToken()).isEmpty();
        assertThat(mcpServerProperties.hasToken()).isFalse();
    }
}
