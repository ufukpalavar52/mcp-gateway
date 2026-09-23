package com.mcpgateway.service.impl;

import com.mcpgateway.client.McpServerClient;
import com.mcpgateway.client.McpServerRefusedException;
import com.mcpgateway.dto.response.PromptResponse;
import com.mcpgateway.domain.enums.LogLevel;
import com.mcpgateway.domain.entity.Definition;
import com.mcpgateway.domain.entity.Run;
import com.mcpgateway.domain.entity.ToolCall;
import com.mcpgateway.domain.entity.User;
import com.mcpgateway.domain.enums.RunStatus;
import com.mcpgateway.repository.RunRepository;
import com.mcpgateway.repository.DefinitionRepository;
import com.mcpgateway.repository.ToolCallRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.SecurityUtils;
import com.mcpgateway.service.intf.AuditService;
import com.mcpgateway.service.intf.ConversationService;
import com.mcpgateway.service.intf.ToolExecutionService;
import com.mcpgateway.common.exception.ResourceNotFoundException;
import com.mcpgateway.mapper.DefinitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Bridges a tool invocation to the MCP server.
 *
 * <p>Ownership is deliberate and one directional: this service owns the database and the
 * catalogue, the MCP server owns the decision. Neither reaches into the other's half —
 * which is why the catalogue is pushed rather than fetched, and why the plan comes back
 * as data rather than as something this service recomputes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecutionServiceImpl implements ToolExecutionService {

    private final McpServerClient mcpServerClient;
    private final DefinitionRepository definitionRepository;
    private final com.mcpgateway.service.DefinitionAccessGuard access;
    private final ToolCallRepository toolCallRepository;
    private final UserRepository userRepository;
    private final DefinitionMapper definitionMapper;
    private final AuditService auditService;
    private final RunRepository runRepository;
    private final ConversationService conversationService;


    /**
     * The tools this caller may run, by name, or empty when they may run anything.
     *
     * <p>This is where the restriction is actually enforced on the prompt path, not the
     * check that follows routing. With {@code execute} set, the MCP server plans and
     * dispatches inside one call, so refusing after a tool name comes back would refuse a
     * job that had already been queued. A tool that is never offered cannot be chosen.
     *
     * <p>Empty for an administrator, and empty means "no restriction" rather than "nothing"
     * — the two have to look different or an administrator would be offered no tools at all.
     */
    private List<String> runnableNames() {
        if (access.unrestricted()) {
            return List.of();
        }

        return access.runnable(definitionRepository.findPublishedTools()).stream()
                .map(Definition::getToolName)
                .toList();
    }

    @Override
    @Transactional
    public McpServerClient.ExecutionResult execute(String toolName, Map<String, Object> arguments,
                                                   Long actionId,
                                                   String expect, List<String> expectAll) {
        Definition definition = definitionRepository.findByToolName(toolName)
                // Not found rather than forbidden: to somebody with no access, a tool they
                // may not reach and one that does not exist are the same fact.
                .filter(access::mayRun)
                .orElseThrow(() -> new ResourceNotFoundException("Tool", toolName));

        String actor = SecurityUtils.currentActorLabel();
        Instant startedAt = Instant.now();

        McpServerClient.ExecutionResult result = withCatalogue(
                () -> mcpServerClient.requestExecution(toolName, arguments, actor,
                        actionId, expect, expectAll));

        recordCall(definition, actor, result, startedAt);
        recordRuns(definition, actor, arguments, result);

        auditService.record("tool.invoked", "definition", definition.getId(),
                Map.of("tool", toolName, "status", result.status()));

        return result;
    }

    // Its own transaction, so the session is open for the whole of it: reading the proposal
    // and then planning, recording and filling it in are one piece of work.
    @Override
    @Transactional
    public PromptResponse approve(Long turnId) {
        ConversationService.Proposal proposal = conversationService.proposalOf(turnId);

        // Checked again here rather than trusted from when the card was drawn. A proposal
        // can sit waiting for days, and access can be taken away in between — approving is
        // the moment the command runs, so it is the moment that has to be permitted.
        definitionRepository.findByToolName(proposal.toolName())
                .filter(access::mayRun)
                .orElseThrow(() -> new ResourceNotFoundException("Tool", proposal.toolName()));

        // Approved by a person, so not unattended however it was first written — and
        // re-planned from the values it was shown with, not from its sentence alone. A step
        // the loop wrote carries no values in its words; asked to find them there, routing
        // produced an id of its own, which did not match the command on screen, so `expect`
        // stopped the dispatch and the card fell back to waiting with nothing said.
        // One card, one decision — however many commands were on it. A plan whose commands
        // all resolve from the one sentence is shown whole, and approving it names every
        // action rather than letting the choice run again: the re-plan an approval performs
        // cannot then come back with a different selection than the one on the screen.
        return prompt(proposal.request(), true, proposal.toolName(),
                proposal.conversationRef(), proposal.goalTurnId(),
                proposal.isBatch() ? proposal.statements() : proposal.statement(),
                proposal.turnId(), false,
                proposal.isBatch() ? proposal.actionIds() : proposal.actionId(),
                proposal.arguments());
    }

    @Override
    @Transactional
    public PromptResponse prompt(String prompt, boolean execute, String toolName,
                                 String conversationRef, Long goalTurnId, Object expect,
                                 Long supersedes, boolean unattended, Object actionId,
                                 java.util.Map<String, String> arguments) {

        String actor = SecurityUtils.currentActorLabel();
        Instant startedAt = Instant.now();

        // What was asked before, so a follow-up has an antecedent. The console kept a
        // thread and looked like a chat, but every prompt reached the MCP server alone:
        // "peki ya example.com icin?" was routed as though it were the first thing
        // anybody had said, and answered accordingly.
        var thread = conversationService.thread(conversationRef);

        McpServerClient.PromptResult result;
        try {
            result = withCatalogue(() -> mcpServerClient.routePrompt(
                    prompt, actor, execute, toolName, thread.turns(), thread.summary(),
                    expect, unattended, actionId, arguments, runnableNames()));
        } catch (RuntimeException failure) {
            // A question that never reached an answer is still part of the conversation.
            // "The MCP server was down when I asked this" is exactly what somebody coming
            // back an hour later needs to see, and a history that dropped these would look
            // as though the question had never been typed.
            if (supersedes == null) {
                conversationService.record(conversationRef, prompt, toolName, execute, null,
                        failure.getMessage(), goalTurnId);
            } else {
                conversationService.complete(supersedes, null, failure.getMessage());
            }
            throw failure;
        }

        // Recorded whether or not a tool was chosen. "Nobody could serve this request" is
        // exactly the kind of thing worth seeing a month of, and a log that only kept the
        // successful routings would never show it.
        Definition definition = result.toolName() == null
                ? null
                : definitionRepository.findByToolName(result.toolName()).orElse(null);

        // The router was given the names this caller may run and should never come back
        // with another one. This is the check that says so out loud if it ever does.
        //
        // It cannot undo anything: with execute set, the MCP server plans and dispatches
        // inside the same call, so by the time a tool name is known the job may already be
        // on the broker. The restriction is enforced by not offering the tool in the first
        // place; this is the alarm, not the lock — which is why it logs at error and why
        // the message says a bug rather than a permission.
        if (definition != null && !access.mayRun(definition)) {
            log.error("Router chose {} for {}, who may not run it. The allowed list sent "
                    + "with the prompt was not honoured.", definition.getToolName(), actor);
            throw new ResourceNotFoundException("Tool", definition.getToolName());
        }

        recordPrompt(definition, actor, prompt, result, startedAt);

        // A prompt that asked to execute dispatches exactly as /execute does, so it needs
        // the same rows waiting for the result. Without them the outcome arrived, found
        // nothing to attach to and was discarded — the work ran and left no trace.
        if (definition != null) {
            recordRuns(definition, actor, result.arguments(), asExecutionResult(result));
        }

        auditService.record("prompt.routed", "definition",
                definition == null ? null : definition.getId(),
                Map.of("tool", result.toolName() == null ? "" : result.toolName(),
                        "status", result.status() == null ? "unmatched" : result.status()));

        ConversationService.Recorded recorded = supersedes == null
                ? conversationService.record(
                        conversationRef, prompt, toolName, execute, result, null, goalTurnId)
                : conversationService.complete(supersedes, result, null);

        return new PromptResponse(recorded.conversationRef(), recorded.turnId(), result);
    }

    /**
     * Views a prompt result as an execution result, for the parts that are the same.
     *
     * <p>They carry the same dispatch: the prompt path decides which tool, then hands the
     * plan to the very same planner and dispatcher. Only the shape of the reply differs.
     */
    private McpServerClient.ExecutionResult asExecutionResult(McpServerClient.PromptResult result) {
        return new McpServerClient.ExecutionResult(
                result.status(), result.plan(), result.dispatch());
    }

    private void recordPrompt(Definition definition, String actor, String prompt,
                              McpServerClient.PromptResult result, Instant startedAt) {

        LogLevel level = switch (result.status() == null ? "" : result.status()) {
            case "planned" -> LogLevel.INFO;
            case "incomplete" -> LogLevel.WARN;
            case "" -> LogLevel.WARN;
            default -> LogLevel.ERROR;
        };

        toolCallRepository.save(ToolCall.builder()
                .definition(definition)
                .toolName(result.toolName() == null ? "(unmatched)" : result.toolName())
                .model(definition == null ? null : definition.getModel())
                .modelLabel(definition == null || definition.getModel() == null
                        ? "" : definition.getModel().getModelId())
                .actor(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .actorLabel(actor)
                .level(level)
                .durationMs((int) Duration.between(startedAt, Instant.now()).toMillis())
                .message(promptMessage(prompt, result))
                .build());
    }

    /**
     * One line describing what the prompt became.
     *
     * <p>The prompt itself is included, truncated. It is what makes the log readable a week
     * later — a row saying only "planned" against a tool name does not tell you what
     * anybody actually asked for.
     */
    private String promptMessage(String prompt, McpServerClient.PromptResult result) {
        String asked = prompt.length() > 200 ? prompt.substring(0, 200) + "…" : prompt;

        if (result.toolName() == null) {
            return "\"" + asked + "\" matched no tool"
                    + (result.problem() == null ? "" : ": " + result.problem());
        }
        return "\"" + asked + "\" -> " + result.toolName() + " (" + result.status() + ")";
    }

    @Override
    @Transactional(readOnly = true)
    public int publishCatalogue() {
        var definitions = definitionRepository.findPublishedTools().stream()
                .map(definitionMapper::toResponse)
                .toList();

        return mcpServerClient.publishCatalogue(definitions);
    }

    /**
     * Runs a call against the MCP server, republishing the catalogue if it has lost it.
     *
     * <p>The MCP server holds its catalogue in memory and reaches back for nothing. That is
     * deliberate and stated in its own source: fetching it would mean that service holding
     * gateway credentials, and an empty catalogue at start up is the price. So a restart
     * there leaves it answering <em>Unknown tool: x. Publish the catalogue first.</em> to
     * every request until somebody notices.
     *
     * <p>Somebody did, twice, by hand. The party that can fix it is this one — it owns the
     * definitions and already holds the credentials — and it was being told the problem in
     * plain words and passing them to the user instead.
     *
     * <p>On the status rather than the sentence. A 404 for a tool this service published
     * means the two copies have diverged, whatever words came with it; matching the message
     * would tie this to a string in another repository that nobody would think to keep.
     *
     * <p>Once. If a fresh catalogue does not fix it the tool really is gone, and a second
     * attempt would turn a clear answer into a loop.
     */
    private <T> T withCatalogue(Supplier<T> call) {
        try {
            return call.get();
        } catch (McpServerRefusedException refusal) {
            if (refusal.getStatus() != HttpStatus.NOT_FOUND) {
                throw refusal;
            }

            log.info("The MCP server does not know this tool; republishing the catalogue");
            publishCatalogue();

            return call.get();
        }
    }

    /**
     * Writes the call into the audit trail.
     *
     * <p>A rejected plan is recorded at {@code error} level: from the caller's side the
     * invocation failed, and a log that showed it as ordinary traffic would hide exactly
     * the events worth looking at.
     */
    private void recordCall(Definition definition, String actor,
                            McpServerClient.ExecutionResult result, Instant startedAt) {

        LogLevel level = switch (result.status()) {
            case "planned" -> LogLevel.INFO;
            case "incomplete" -> LogLevel.WARN;
            default -> LogLevel.ERROR;
        };

        toolCallRepository.save(ToolCall.builder()
                .definition(definition)
                .toolName(definition.getToolName())
                .model(definition.getModel())
                .modelLabel(definition.getModel() == null ? "" : definition.getModel().getModelId())
                .actor(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .actorLabel(actor)
                .level(level)
                .durationMs((int) Duration.between(startedAt, Instant.now()).toMillis())
                .message(describe(result))
                .build());
    }


    /**
     * Opens a row per dispatched action, so the executor's result has somewhere to land.
     *
     * <p>Created here rather than when the result arrives, for two reasons. A run that is
     * in flight is visible while it is in flight, instead of appearing only once it has
     * finished; and a result that arrives for a run nobody recorded can be recognised as
     * the anomaly it is rather than quietly inserted.
     *
     * <p>Only for a queued dispatch. A plan that was skipped or refused never reached an
     * executor, and a row waiting forever for a result that cannot come is worse than none.
     */
    private void recordRuns(Definition definition, String actor, Map<String, Object> arguments,
                            McpServerClient.ExecutionResult result) {

        if (result.dispatch() == null || !"queued".equals(result.dispatch().get("status"))) {
            return;
        }

        String runRef = String.valueOf(result.dispatch().get("run_id"));
        Object actionRunIds = result.dispatch().get("action_run_ids");

        if (!(actionRunIds instanceof Map<?, ?> byAction)) {
            log.warn("Dispatch for {} carried no action run ids; nothing to track", runRef);
            return;
        }

        User actorUser = SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null);

        Map<Long, Map<String, Object>> planned = plannedByAction(result);

        for (Map.Entry<?, ?> entry : byAction.entrySet()) {
            Long actionId = Long.valueOf(String.valueOf(entry.getKey()));
            Map<String, Object> plan = planned.getOrDefault(actionId, Map.of());
            String resolved = String.valueOf(plan.getOrDefault("resolved", ""));
            boolean isQuery = "db".equals(plan.get("kind"));

            runRepository.save(Run.builder()
                    .definition(definition)
                    .action(definition.getActions().stream()
                            .filter(candidate -> candidate.getId().equals(actionId))
                            .findFirst()
                            .orElse(null))
                    .actor(actorUser)
                    .actorLabel(actor)
                    // Running, not pending: the broker has the job and an executor will
                    // pick it up. Pending would suggest something here still has to act.
                    .status(RunStatus.RUNNING)
                    .inputs(arguments == null ? Map.of() : arguments)
                    .runRef(runRef)
                    .actionRef(String.valueOf(entry.getValue()))
                    // The plan's own text, which is masked. Worth keeping even though the
                    // executor is about to run it: for a dynamic action this is the only
                    // record of what a model wrote, and "it failed" is not an answer to
                    // "what did it try to run".
                    .generatedQuery(isQuery ? blankToNull(resolved) : null)
                    .generatedCommand(isQuery ? null : blankToNull(resolved))
                    .startedAt(Instant.now())
                    .build());
        }
    }

    /** The plan's actions, by the action they resolved. */
    private Map<Long, Map<String, Object>> plannedByAction(McpServerClient.ExecutionResult result) {
        Object actions = result.plan() == null ? null : result.plan().get("actions");

        if (!(actions instanceof java.util.List<?> list)) {
            return Map.of();
        }

        Map<Long, Map<String, Object>> byAction = new java.util.HashMap<>();

        for (Object item : list) {
            if (item instanceof Map<?, ?> action && action.get("action_id") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) action;
                byAction.put(Long.valueOf(String.valueOf(action.get("action_id"))), typed);
            }
        }

        return byAction;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String describe(McpServerClient.ExecutionResult result) {
        Object problems = result.plan() == null ? null : result.plan().get("problems");

        if (problems instanceof java.util.List<?> list && !list.isEmpty()) {
            return String.join("; ", list.stream().map(String::valueOf).toList());
        }
        return "Plan status: " + result.status();
    }
}
