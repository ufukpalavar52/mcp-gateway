package com.mcpgateway.service.impl;

import com.mcpgateway.client.McpServerUnavailableException;
import com.mcpgateway.event.CatalogueChangedEvent;
import com.mcpgateway.property.McpServerProperties;
import com.mcpgateway.service.intf.ToolExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the MCP server's catalogue in step with this service's definitions.
 *
 * <p>The MCP server holds its catalogue in memory and starts empty, so something has to
 * push it. That happens on start up and after every committed change.
 *
 * <p>A failed publish is logged, never rethrown. The MCP server being unreachable must
 * not stop this service from starting, and must not roll back a definition the user
 * successfully saved — the definition is committed and correct either way, and
 * {@code POST /api/v1/tools/publish} exists to close the gap once the MCP server is back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CataloguePublisher {

    private final ToolExecutionService toolExecutionService;
    private final McpServerProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void publishOnStartUp() {
        if (!properties.isAutoPublish()) {
            log.info("Catalogue auto publish is off; the MCP server keeps whatever it holds");
            return;
        }
        publish("start up");
    }

    /**
     * Republishes after a definition change commits.
     *
     * <p>{@code AFTER_COMMIT} rather than immediately: publishing inside the transaction
     * would let a later rollback leave the MCP server exposing a definition this service
     * no longer has.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishOnChange(CatalogueChangedEvent event) {
        if (properties.isAutoPublish()) {
            publish(event.reason());
        }
    }

    /**
     * Publishes, and treats any failure as a warning rather than an error.
     *
     * <p>Every {@code RuntimeException} is caught, not only an unreachable MCP server:
     * this runs from a start-up hook and from an after-commit hook, and in both places a
     * throw does damage out of all proportion to the problem. From start up it aborts the
     * context, so a downstream service being unavailable — or the database not being
     * reachable yet — would stop this one from coming up at all. From after commit the
     * definition is already saved, and failing there would report an error for work that
     * succeeded.
     *
     * <p>What is lost is the immediate signal, so the log line says what to do about it.
     * {@code POST /api/v1/tools/publish} reports failures to its caller normally.
     */
    private void publish(String reason) {
        try {
            int published = toolExecutionService.publishCatalogue();
            log.info("Catalogue published after {}: {} tool(s)", reason, published);
        } catch (McpServerUnavailableException ex) {
            log.warn("Catalogue not published after {}: {}. The MCP server keeps its "
                    + "previous catalogue; republish with POST /api/v1/tools/publish",
                    reason, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Catalogue not published after {}. Republish with "
                    + "POST /api/v1/tools/publish once the cause is resolved", reason, ex);
        }
    }
}
