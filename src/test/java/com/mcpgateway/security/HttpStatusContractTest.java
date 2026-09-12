package com.mcpgateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks in the status code an unmatched or misused URL produces.
 *
 * <p>The security chain runs before the DispatcherServlet, so the default behaviour was
 * to answer {@code 401} for every unknown path: a typo looked exactly like a missing
 * token. These tests pin the corrected contract from both sides of authentication.
 *
 * <p>No PostgreSQL or Redis needed: none of these requests reach a service.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.hikari.connection-timeout=250"
})
@AutoConfigureMockMvc
class HttpStatusContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Without a token")
    class Anonymous {

        @Test
        void unknownPathIsNotFound() throws Exception {
            mockMvc.perform(get("/api/v1/no-such-thing"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }

        @Test
        void pathOutsideTheApiIsAlsoNotFound() throws Exception {
            mockMvc.perform(get("/completely-unrelated"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void protectedPathStillDemandsAToken() throws Exception {
            mockMvc.perform(get("/api/v1/models"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
        }

        @Test
        void knownPathWithTheWrongVerbIsMethodNotAllowed() throws Exception {
            mockMvc.perform(get("/api/v1/auth/login"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));

            mockMvc.perform(patch("/api/v1/models"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        void publicPathIsReachedAndValidated() throws Exception {
            // Reaching bean validation proves the request got past the security chain.
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    @DisplayName("With a token")
    @WithMockUser(roles = "ADMIN")
    class Authenticated {

        /** Would have been a 500 from the catch-all handler before the fix. */
        @Test
        void unknownPathIsNotFound() throws Exception {
            mockMvc.perform(get("/api/v1/no-such-thing"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }

        @Test
        void wrongVerbIsMethodNotAllowed() throws Exception {
            mockMvc.perform(patch("/api/v1/models"))
                    .andExpect(status().isMethodNotAllowed());
        }

        /** A non numeric id must be a client error, not a server error. */
        @Test
        void unconvertiblePathVariableIsBadRequest() throws Exception {
            mockMvc.perform(get("/api/v1/models/not-a-number"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
        }

    }
}
