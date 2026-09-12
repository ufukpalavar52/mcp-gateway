package com.mcpgateway.service;

import com.mcpgateway.client.McpServerClient;
import com.mcpgateway.domain.entity.Run;
import com.mcpgateway.domain.entity.RunTarget;
import com.mcpgateway.domain.enums.RunStatus;
import com.mcpgateway.property.ConversationProperties;
import com.mcpgateway.repository.RunRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.AuthenticatedUser;
import com.mcpgateway.service.intf.ConversationService;
import com.mcpgateway.service.intf.ToolExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Carries one goal through more than one query.
 *
 * <p>A prompt could need two questions and only ever asked one: "ismi ali ve veli olanları
 * getir iki ayrı tablo olarak" returned the accounts called ali and stopped, and the other
 * half had to be typed again as its own question. The router picks one tool, that tool's
 * action writes one statement, and a guardrail refuses two statements in one — so a goal
 * had nowhere to become two.
 *
 * <p>Driven by results arriving rather than by waiting for them. A step cannot be decided
 * until the one before it has answered, and a prompt that blocked for that would hold an
 * HTTP request open for as long as the database takes.
 *
 * <p>Two kinds of goal, and they are not the same proposition. A goal made of queries runs
 * its steps: they only read, and a read that was not wanted costs a row count. A goal made
 * of shell commands does not — the loop writes the step and stops, and a person approves it
 * before anything reaches a machine. Choosing your own next query and choosing your own
 * next command against a real server are different powers, and only the first is taken
 * without asking.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalLoop {

    private static final int SAMPLE_ROWS = 3;
    private static final int OUTPUT_LINES = 8;
    private static final int OUTPUT_CHARACTERS = 1_000;

    private final ConversationService conversationService;
    private final ToolExecutionService toolExecutionService;
    private final McpServerClient mcpServerClient;
    private final RunRepository runRepository;
    private final UserRepository userRepository;
    private final RunOutputCipher outputCipher;
    private final ConversationProperties properties;
    private final GoalProgress progress;

    /**
     * Decides whether a finished run's goal needs another step, and takes it.
     *
     * <p>Every reason to stop is checked here rather than asked of a model: the step
     * budget, a step that failed, work that was not a read. A loop that asks whether to
     * continue can be told yes forever.
     */
    public void advance(Run finished) {
        if (!properties.isGoalLoop() || finished.getRunRef() == null) {
            return;
        }

        if (finished.getStatus() != RunStatus.SUCCEEDED || !"execute".equals(finished.getPurpose())) {
            // A failed step ends the goal. The operator can see what went wrong and say
            // what to do; guessing a different query is how one bad step becomes five.
            return;
        }

        String kind = finished.getAction() == null
                ? null
                : finished.getAction().getKind().name().toLowerCase();

        if (!"db".equals(kind) && !"ssh".equals(kind) && !"rest".equals(kind)) {
            return;
        }

        ConversationService.Goal goal = conversationService.goalOf(finished.getRunRef());

        if (goal == null || goal.toolName() == null || goal.owner() == null) {
            return;
        }

        if (goal.steps().size() >= properties.getGoalSteps()) {
            log.info("Goal {} stopped at the {} step limit", goal.turnId(), properties.getGoalSteps());
            return;
        }

        // What the plan already knew is read rather than asked about. An action set aside
        // "waiting on id" is a recorded fact; asking a model whether there is more to do
        // got "done" twice, from a reason describing the deletion it had not performed.
        Map<String, Object> waiting = goal.pending().isEmpty() ? null : goal.pending().getFirst();

        // Said out loud, and for the whole of it. This is the gap in which the console had
        // nothing to show and looked stopped — and it is two model calls, not one: deciding
        // what comes next, and then planning it. Clearing after the first would put the
        // indicator out several seconds before the card it was announcing.
        progress.deciding(goal.conversationRef());
        try {
            McpServerClient.Step next = mcpServerClient.nextStep(
                    goal.request(), goal.toolName(), describe(goal.steps(), kind),
                    List.of(), "", properties.getGoalSteps(), kind,
                    waiting == null ? "" : String.valueOf(waiting.get("waitingFor")));

            if (next.request() == null || next.request().isBlank()
                    || (next.done() && waiting == null)) {
                log.info("Goal {} is met after {} step(s): {}",
                        goal.turnId(), goal.steps().size(), next.reason());
                return;
            }

            if (waiting != null) {
                // Taken up whatever happens next. A step that fails is the operator's to
                // see and decide on; offering it again on the next result would be a loop.
                conversationService.takeUp(goal.turnId(), waiting.get("actionId"));
            }

            take(goal, next, waiting == null ? null : waiting.get("actionId"));
        } finally {
            progress.settled(goal.conversationRef());
        }
    }

    /**
     * Runs the next step as the person whose goal it is.
     *
     * <p>Their identity, not the process's: this is their conversation, the step is planned
     * against their permissions, and an audit trail that said "system" for work somebody
     * asked for would be describing the wrong actor.
     */
    private void take(ConversationService.Goal goal, McpServerClient.Step next,
                      Object actionId) {
        var owner = userRepository.findById(goal.owner()).orElse(null);

        if (owner == null) {
            log.warn("Goal {} has no owner to continue as", goal.turnId());
            return;
        }

        var previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(owner.getId(), owner.getEmail(),
                                owner.getRole(), "goal-loop"),
                        null, List.of()));

        try {
            log.info("Goal {} continues: {}", goal.turnId(), next.request());

            // Sent to run, and marked as written by nobody. Whether it actually runs is
            // decided where the plan is known: a step that turns out to change something
            // is held for approval, and the console shows the command it resolved to.
            //
            // Deciding it here was the defect. This code sees the action that *finished* —
            // a search, a read — and the one about to run is not known until it has been
            // planned. A goal that found a user and then deleted them ran the delete
            // unattended, because the step before it was a GET.
            toolExecutionService.prompt(
                    next.request(), true, goal.toolName(), goal.conversationRef(),
                    goal.turnId(), "", null, true, actionId, next.values());
        } catch (RuntimeException failure) {
            // The step is lost and the goal ends there. Retrying from inside a queue
            // listener would turn one bad step into a loop nobody asked for.
            log.warn("Goal {} could not take its next step: {}",
                    goal.turnId(), failure.getMessage());
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    /** Each step as the step planner reads it: what was asked, and the shape of the answer. */
    private List<McpServerClient.TakenStep> describe(
            List<ConversationService.Goal.Step> steps, String kind) {

        List<McpServerClient.TakenStep> described = new ArrayList<>();

        for (ConversationService.Goal.Step step : steps) {
            if ("ssh".equals(kind) || "rest".equals(kind)) {
                // Both answer in text rather than in rows: what a command printed, and
                // what an endpoint replied. A row count would describe neither.
                described.add(new McpServerClient.TakenStep(
                        step.request(), step.statement(), null, List.of(), "",
                        printed(step.runRef()), step.failure()));
                continue;
            }

            List<Map<String, Object>> rows = rowsOf(step.runRef());

            described.add(new McpServerClient.TakenStep(
                    step.request(),
                    step.statement(),
                    rows == null ? null : rows.size(),
                    rows == null || rows.isEmpty() ? List.of() : List.copyOf(rows.getFirst().keySet()),
                    sample(rows),
                    "",
                    step.failure()));
        }

        return described;
    }

    /**
     * What a shell step printed, opened for this decision and not kept.
     *
     * <p>Trimmed hard. Deciding "was it installed" needs the last few lines, and a whole
     * dnf transaction would spend the model's attention on a package list it has no
     * question about — as well as putting more of a production machine's output into a
     * prompt than the decision requires.
     */
    private String printed(String runRef) {
        if (runRef == null) {
            return "";
        }

        for (Run run : runRepository.findByRunRefOrderByIdAsc(runRef)) {
            for (RunTarget target : run.getTargets()) {
                String text = target.getOutputSealed() != null
                        ? outputCipher.open(target.getOutputSealed(), target.getOutputKeyId()).text()
                        : target.getStdoutExcerpt();

                if (text != null && !text.isBlank()) {
                    return trim(text);
                }
            }
        }

        return "";
    }

    /** The tail of an output, capped. The end of a command's output is the part that says
     * whether it worked. */
    private static String trim(String text) {
        String[] lines = text.strip().split("\n");
        int from = Math.max(0, lines.length - OUTPUT_LINES);
        String tail = String.join("\n", List.of(lines).subList(from, lines.length));

        return tail.length() <= OUTPUT_CHARACTERS ? tail
                : tail.substring(tail.length() - OUTPUT_CHARACTERS);
    }

    /**
     * The rows a step returned, opened for this decision and not kept.
     *
     * <p>Opened here and discarded when the call returns. Storing the summary would put
     * production values back into a column in the clear, which is the arrangement the
     * sealing exists to end.
     */
    private List<Map<String, Object>> rowsOf(String runRef) {
        if (runRef == null) {
            return null;
        }

        for (Run run : runRepository.findByRunRefOrderByIdAsc(runRef)) {
            for (RunTarget target : run.getTargets()) {
                if (target.getOutputSealed() != null) {
                    return outputCipher.open(
                            target.getOutputSealed(), target.getOutputKeyId()).rows();
                }
                if (target.getResultRows() != null) {
                    return target.getResultRows();
                }
            }
        }
        return null;
    }

    /**
     * The first few rows, rendered.
     *
     * <p>A handful rather than all of them: what the next step needs is the shape — that
     * the ids are numbers and the domains are strings — and a thousand rows of it would
     * spend the model's attention on data it has no question about.
     */
    private String sample(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }

        return rows.stream()
                .limit(SAMPLE_ROWS)
                .map(row -> row.values().stream()
                        .map(value -> value == null ? "NULL" : String.valueOf(value))
                        .reduce((left, right) -> left + "  " + right)
                        .orElse(""))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
