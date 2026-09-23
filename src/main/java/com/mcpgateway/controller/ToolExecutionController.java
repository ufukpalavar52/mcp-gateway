package com.mcpgateway.controller;

import com.mcpgateway.client.McpServerClient;
import com.mcpgateway.dto.request.PromptRequest;
import com.mcpgateway.dto.request.ToolExecuteRequest;
import com.mcpgateway.dto.response.PromptResponse;
import com.mcpgateway.service.intf.ConversationService;
import com.mcpgateway.service.intf.ToolExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Invoking a tool, and keeping the MCP server's catalogue in step.
 *
 * <p>Nothing here decides anything: the request is handed to the MCP server, and what
 * comes back is a plan. Whether that plan is ever carried out is the executor's business.
 */
@RestController
@RequestMapping("/api/v1/tools")
@RequiredArgsConstructor
public class ToolExecutionController {

    private final ToolExecutionService toolExecutionService;
    private final ConversationService conversationService;

    /**
     * Asks the MCP server what this call resolves to, and runs it unless it needs a yes.
     *
     * <p>An action its operator marked as needing approval comes back planned and
     * undispatched. Approving it is this same call again carrying {@code expect}: the
     * command that was on the screen. Approval is of a command rather than of an
     * intention, because planning is not deterministic and agreement to one sentence must
     * not carry to whatever the next plan happens to say.
     */
    @PostMapping("/{toolName}/execute")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<McpServerClient.ExecutionResult> execute(
            @PathVariable String toolName,
            @RequestBody(required = false) ToolExecuteRequest request) {

        ToolExecuteRequest asked =
                request == null ? new ToolExecuteRequest(null, null, null, null) : request;

        return ResponseEntity.ok(toolExecutionService.execute(
                toolName, asked.arguments(), asked.actionId(),
                asked.expect(), asked.expectAll()));
    }

    /**
     * Routes a sentence to a tool and returns what it would do.
     *
     * <p>Nothing runs unless {@code execute} is set. Deciding what a request means and
     * acting on it are different things, and a console that did both by default would make
     * the second one invisible.
     */
    @PostMapping("/prompt")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<PromptResponse> prompt(@Valid @RequestBody PromptRequest request) {
        return ResponseEntity.ok(
                toolExecutionService.prompt(
                        request.prompt(), request.shouldExecute(), request.toolName(),
                        request.conversationRef(), null, "", null, false, null,
                        java.util.Map.of()));
    }

    /**
     * Runs a step the goal loop wrote and did not run.
     *
     * <p>A goal made of shell commands stops at each step and waits: the loop chose the
     * command, nobody typed it, and the machine it lands on is real. This is where a person
     * says yes.
     *
     * <p>The turn is re-planned rather than replayed. What was stored is a sentence and the
     * command it resolved to at the time; running it means going back through routing,
     * planning and the guardrails, exactly as the first attempt did. A command kept from
     * ten minutes ago and executed on trust would be the one path into the executor that
     * skipped every check.
     */
    @PostMapping("/steps/{turnId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<PromptResponse> approve(@PathVariable Long turnId) {
        return ResponseEntity.ok(toolExecutionService.approve(turnId));
    }

    /**
     * Turns down a step that was waiting for approval.
     *
     * <p>The other half of being asked. Without it the only way past a proposal is to
     * approve it, which makes the question rhetorical.
     */
    @PostMapping("/steps/{turnId}/decline")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<Void> decline(@PathVariable Long turnId) {
        conversationService.decline(turnId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Republishes the catalogue.
     *
     * <p>Normally automatic; exposed so a catalogue can be pushed again after the MCP
     * server restarts, without waiting for the next definition change.
     */
    @PostMapping("/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> publish() {
        return ResponseEntity.ok(Map.of("published", toolExecutionService.publishCatalogue()));
    }
}
