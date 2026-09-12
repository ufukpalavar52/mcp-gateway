package com.mcpgateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Context load check.
 *
 * <p>Requires a reachable PostgreSQL and Redis with the schema already applied by the
 * Liquibase container. It is therefore opt in: set
 * {@code RUN_INTEGRATION_TESTS=true} to include it, so a plain {@code mvn test} on a
 * machine without those services still passes.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class McpGatewayApplicationTests {

    @Test
    void contextLoads() {
        // Fails if any bean cannot be created or the schema does not match the entities.
    }
}
