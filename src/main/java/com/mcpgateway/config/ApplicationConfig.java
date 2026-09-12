package com.mcpgateway.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Cross cutting infrastructure switches.
 *
 * <p>JPA auditing fills {@code created_at} and {@code updated_at}; the schema carries
 * no triggers, so the application owns those columns.
 *
 * <p>Configuration properties are discovered by scanning {@code com.mcpgateway.property}
 * rather than being listed one by one, so adding a settings class needs no change here.
 */
@Configuration
@EnableJpaAuditing
@EnableTransactionManagement
@ConfigurationPropertiesScan("com.mcpgateway.property")
public class ApplicationConfig {
}
