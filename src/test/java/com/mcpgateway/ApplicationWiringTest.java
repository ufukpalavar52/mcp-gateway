package com.mcpgateway;

import com.mcpgateway.controller.AuthController;
import com.mcpgateway.controller.DefinitionController;
import com.mcpgateway.security.JwtAuthenticationFilter;
import com.mcpgateway.security.TokenStore;
import com.mcpgateway.service.intf.AuthService;
import com.mcpgateway.service.intf.DefinitionService;
import com.mcpgateway.validation.aspect.BusinessRuleValidationAspect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the whole object graph can be constructed.
 *
 * <p>Unlike the full context test this one needs no PostgreSQL and no Redis: the dialect
 * is stated explicitly so Hibernate never asks the driver for metadata, and Lettuce
 * connects lazily on first use. What remains
 * under test is exactly what unit tests cannot see: component scanning, constructor
 * injection, the security filter chain and the aspect proxying.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/wiring-test",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.hikari.connection-timeout=250",
        "mcp.jwt.secret=wiring-test-secret-that-is-long-enough-for-hs256"
})
class ApplicationWiringTest {

    @Autowired
    private AuthController authController;

    @Autowired
    private DefinitionController definitionController;

    @Autowired
    private AuthService authService;

    @Autowired
    private DefinitionService definitionService;

    @Autowired
    private TokenStore tokenStore;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Autowired
    private BusinessRuleValidationAspect businessRuleValidationAspect;

    @Test
    void everyLayerIsWired() {
        assertThat(authController).isNotNull();
        assertThat(definitionController).isNotNull();
        assertThat(authService).isNotNull();
        assertThat(definitionService).isNotNull();
        assertThat(tokenStore).isNotNull();
        assertThat(jwtAuthenticationFilter).isNotNull();
        assertThat(securityFilterChain).isNotNull();
        assertThat(businessRuleValidationAspect).isNotNull();
    }

    /**
     * The validation aspect is only useful if it actually intercepts.
     *
     * <p>A controller carrying {@code @ValidateBusinessRules} must therefore be an AOP
     * proxy; a plain instance would mean the advice silently never runs.
     */
    @Test
    void controllersWithBusinessRulesAreProxied() {
        assertThat(AopUtils.isAopProxy(definitionController))
                .as("DefinitionController must be proxied for @ValidateBusinessRules to apply")
                .isTrue();
    }
}
