package com.mcpgateway.service.intf;

import com.mcpgateway.client.McpServerClient;
import com.mcpgateway.dto.response.PromptResponse;

import java.util.Map;

/** Invokes a published tool through the MCP server and records what came back. */
public interface ToolExecutionService {

    /**
     * Asks the MCP server to decide what {@code toolName} resolves to.
     *
     * <p>The decision belongs to the MCP server; this service only supplies the caller's
     * identity and writes the audit trail, because it is the one that owns the database.
     */
    McpServerClient.ExecutionResult execute(String toolName, Map<String, Object> arguments);

    /**
     * Routes a prompt to a tool and records what it turned into.
     *
     * <p>The choice belongs to the MCP server, as the plan does. This service supplies the
     * caller's identity, decides nothing, and writes the audit trail — including for a
     * prompt that matched no tool, which is worth knowing about.
     *
     * <p>The turn is also appended to the caller's console session, so that closing the tab
     * no longer loses the question along with the answer.
     *
     * <p>{@code goalTurnId} marks this as a step of an earlier goal rather than a question
     * of its own. Null for anything a person typed.
     *
     * <p>{@code expect} is the command somebody was shown and approved. Planning is not
     * deterministic, so a step approved on the strength of one command can be re-planned
     * into another; set this and nothing is dispatched unless the plan still reads the
     * same. Blank for anything nobody has approved in particular.
     */
    PromptResponse prompt(String prompt, boolean execute, String toolName,
                          String conversationRef, Long goalTurnId, Object expect,
                          Long supersedes, boolean unattended, Object actionId,
                          java.util.Map<String, String> arguments);

    /**
     * Runs a step somebody approved.
     *
     * <p>The turn id, not the command. What runs is planned again from the sentence, so it
     * goes through routing, the planner and the guardrails exactly as a typed question
     * does; the command that was on screen is carried along only to be compared with what
     * comes back, and nothing is dispatched if the two differ.
     *
     * <p>The approved turn is filled in rather than answered beside. A second turn left the
     * first one still planned and still with no run behind it, so it went on offering to be
     * approved and every press ran the command again.
     */
    PromptResponse approve(Long turnId);

    /** Pushes the current catalogue so the MCP server exposes what this service holds. */
    int publishCatalogue();
}
