package com.mcpgateway.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.mcpgateway.property.McpServerProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the client actually puts on the wire.
 *
 * <p>Against a real socket rather than {@code MockRestServiceServer}: the client pins its
 * own request factory in the constructor — Uvicorn will not do the h2c upgrade the JDK
 * client opens with — which replaces the one the mock server installs, and every request
 * goes out unintercepted.
 */
class McpServerClientTest {

    private static final String PLANNED = """
            {"status":"planned","toolName":"users","plan":{"actions":[]}}""";

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> received = new AtomicReference<>();

    private HttpServer server;
    private McpServerClient client;

    @BeforeEach
    void listen() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/prompts", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] body = PLANNED.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        McpServerProperties properties = new McpServerProperties();
        properties.setUrl("http://127.0.0.1:" + server.getAddress().getPort());

        client = new McpServerClient(RestClient.builder(), properties);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private JsonNode sent() throws IOException {
        return mapper.readTree(received.get());
    }

    @Nested
    @DisplayName("the values a step already read are sent with it")
    class TheValuesAStepReadAreSent {

        /**
         * They were accepted as a parameter and then never put in the body.
         *
         * <p>Nothing failed. The MCP server simply received a step with no values and was
         * left to recover them from the sentence the loop had written — "the first user
         * found with first_name 'Yigit'", which contains no id. It produced one anyway:
         * {@code DELETE /api/users/1}, for a goal whose search had returned 59, 69, 86
         * and 97.
         *
         * <p>Asserted on the wire rather than on the call, because the defect was entirely
         * between the two: every caller passed its values correctly.
         */
        @Test
        void an_argument_reaches_the_request_body() throws IOException {
            client.routePrompt("sil", "u@e", true, "users", List.of(), "", "",
                    true, 4, Map.of("id", "59"));

            assertThat(sent().path("arguments").path("id").asText()).isEqualTo("59");
        }

        @Test
        void nothing_is_sent_when_there_is_nothing_to_send() throws IOException {
            // An ordinary prompt has none, and an empty object is not the same as absent.
            client.routePrompt("getir", "u@e", true, "users", List.of(), "", "",
                    false, null, Map.of());

            assertThat(sent().has("arguments")).isFalse();
        }
    }
}
