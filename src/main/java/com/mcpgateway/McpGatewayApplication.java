package com.mcpgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/*
 * UserDetailsServiceAutoConfiguration is excluded: authentication is entirely JWT based,
 * so the in-memory default user it would create is never consulted. Leaving it on only
 * printed a generated password into the log on every start, which reads like a real
 * credential and is not one.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class McpGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpGatewayApplication.class, args);
    }

}
