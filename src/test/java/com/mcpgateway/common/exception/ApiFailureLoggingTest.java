package com.mcpgateway.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mcpgateway.client.McpServerUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which failures leave a trace.
 *
 * <p>On 21 September a prompt came back "Could not route the prompt: I/O error … Request
 * cancelled" and the gateway log held nothing at all for that minute — not the error, not
 * the request. The MCP server's log showed two model calls answering normally, so the one
 * process that knew why the call was abandoned was the one that said nothing.
 *
 * <p>An error somebody can read on a screen and not find in a log cannot be diagnosed a
 * second time. These two cases are the rule that fixes it, and the rule has two halves:
 * logging the outages is only useful if the ordinary refusals stay quiet.
 */
class ApiFailureLoggingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ListAppender<ILoggingEvent> written = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void listen() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        written.start();
        logger.addAppender(written);
    }

    @AfterEach
    void stopListening() {
        logger.detachAppender(written);
        written.stop();
    }

    private static MockHttpServletRequest asking(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void anOutageIsLoggedWithItsCause() {
        // The shape of the real one: the useful description is in the cause, and the
        // message names the intention rather than what went wrong.
        McpServerUnavailableException outage = new McpServerUnavailableException(
                "Could not route the prompt",
                new RuntimeException("I/O error on POST request: Request cancelled"));

        handler.handleApiException(outage, asking("/api/v1/prompts"));

        assertThat(written.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("/api/v1/prompts");

            // Without the cause the entry says an intention and no failure, which is the
            // same as saying nothing.
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getCause().getMessage())
                    .contains("Request cancelled");
        });
    }

    @Test
    void anOrdinaryRefusalStaysQuiet() {
        // A tool somebody may not reach is normal traffic. Logging these buries the
        // entries that matter among the ones that do not, which is how the outage above
        // would go unnoticed even after it started being written down.
        handler.handleApiException(
                new ResourceNotFoundException("Definition", "42"), asking("/api/v1/tools/42"));

        assertThat(written.list).isEmpty();
    }
}
