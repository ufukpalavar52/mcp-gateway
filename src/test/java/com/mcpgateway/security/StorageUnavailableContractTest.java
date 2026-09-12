package com.mcpgateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What a request gets when the database is gone.
 *
 * <p>Its own context, pointed at a port nothing listens on. The neighbouring
 * {@link HttpStatusContractTest} deliberately never reaches a service, so it can share a
 * context with a reachable database; this one must fail at the database, and pointing it
 * anywhere real would make the result depend on whether PostgreSQL happened to be
 * running.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unreachable",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.hikari.connection-timeout=250",
        "mcp.server.auto-publish=false"
})
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class StorageUnavailableContractTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * An unreachable database is a {@code 503}, not a {@code 500}.
     *
     * <p>The request fails where a real outage fails it: opening the transaction. That
     * throws {@code CannotCreateTransactionException}, which belongs to the transaction
     * hierarchy rather than {@code DataAccessException}, so it used to slip past the
     * storage handler and be answered {@code 500 INTERNAL_ERROR} — telling the caller
     * their request was at fault when the truth was "retry later".
     */
    @Test
    void unreachableDatabaseIsServiceUnavailable() throws Exception {
        mockMvc.perform(get("/api/v1/models"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("STORAGE_UNAVAILABLE"));
    }
}
