package com.mcpgateway.service.impl;

import com.mcpgateway.client.McpServerClient;
import com.mcpgateway.common.dto.PageResponse;
import com.mcpgateway.common.exception.ResourceNotFoundException;
import com.mcpgateway.domain.entity.Conversation;
import com.mcpgateway.domain.entity.ConversationTurn;
import com.mcpgateway.domain.entity.User;
import com.mcpgateway.dto.response.ConversationResponse;
import com.mcpgateway.repository.ConversationRepository;
import com.mcpgateway.repository.ConversationTurnRepository;
import com.mcpgateway.property.ConversationProperties;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.SecurityUtils;
import com.mcpgateway.service.intf.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Console sessions.
 *
 * <p>The turns are the conversation and nothing more: what was asked, what answered, and a
 * reference to the run that holds the output. Deliberately not the output itself. A result
 * set copied here would be a second place personal data lives, and the one nobody thinks of
 * when it has to be removed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    /** How much of the first question becomes the title. Enough to recognise, not to read. */
    private static final int TITLE_LIMIT = 80;

    private final ConversationRepository conversationRepository;
    private final ConversationTurnRepository turnRepository;
    private final UserRepository userRepository;
    private final McpServerClient mcpServerClient;
    private final ConversationProperties properties;
    private final com.mcpgateway.service.GoalProgress goalProgress;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> findMine(String search, Pageable pageable) {
        String wanted = search == null ? "" : search.trim();

        Page<Conversation> page = wanted.isEmpty()
                ? conversationRepository.findByOwnerId(currentUserId(), pageable)
                : conversationRepository.search(
                        currentUserId(), "%" + wanted.toLowerCase(Locale.ROOT) + "%", pageable);

        return PageResponse.from(page, conversation -> toResponse(conversation, false));
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse open(String conversationRef) {
        return toResponse(mine(conversationRef), true);
    }

    @Override
    @Transactional(readOnly = true)
    public Goal goalOf(String runRef) {
        ConversationTurn turn = turnRepository.findByRunRef(runRef).orElse(null);

        if (turn == null) {
            return null;
        }

        // The turn that started it, which is this one unless it is itself a step.
        ConversationTurn goal = turn.getGoalTurn() == null ? turn : turn.getGoalTurn();

        List<ConversationTurn> taken = new java.util.ArrayList<>();
        taken.add(goal);
        taken.addAll(turnRepository.findByGoalTurnIdOrderByIdAsc(goal.getId()));

        // Everything still waiting, across every turn of this goal. Taking one up clears
        // its entry, so what is left here is what nothing has answered for yet.
        List<Map<String, Object>> pending = taken.stream()
                .map(ConversationTurn::getDeferred)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();

        return new Goal(
                goal.getId(),
                goal.getConversation().getConversationRef(),
                goal.getPrompt(),
                goal.getToolName(),
                goal.getConversation().getOwner().getId(),
                taken.stream().map(this::asStep).toList(),
                pending);
    }

    /**
     * One step: what was asked, and where its answer can be read.
     *
     * <p>The shape of the answer is filled in by the caller, which can open a run's sealed
     * output. This service holds the conversation and has no business decrypting one.
     */
    private Goal.Step asStep(ConversationTurn turn) {
        return new Goal.Step(
                turn.getPrompt(), turn.getStatement(), turn.getRunRef(), turn.getFailure());
    }

    @Override
    @Transactional
    public void decline(Long turnId) {
        Proposal proposal = proposalOf(turnId);

        ConversationTurn turn = turnRepository.findById(turnId).orElseThrow();

        // Not "rejected": that is what the guardrails say about a command they refused, and
        // this one was fine. A person looked at it and said no, which is a different fact
        // and deserves a different word.
        turn.setStatus("declined");
        turn.setExecuted(true);
        turnRepository.saveAndFlush(turn);

        // The goal stops offering it. Turning a step down is a decision about the goal, not
        // a request to be asked again when the next result lands.
        if (proposal.goalTurnId() != null) {
            takeUp(proposal.goalTurnId(), actionOf(turn));
        }
    }

    /** The action a proposal was for, when its deferral recorded one. */
    private Object actionOf(ConversationTurn turn) {
        List<Map<String, Object>> waiting = turn.getDeferred();

        return waiting == null || waiting.isEmpty() ? null : waiting.getFirst().get("actionId");
    }

    @Override
    @Transactional
    public void takeUp(Long goalTurnId, Object actionId) {
        if (goalTurnId == null || actionId == null) {
            return;
        }

        for (ConversationTurn turn : withGoal(goalTurnId)) {
            List<Map<String, Object>> waiting = turn.getDeferred();

            if (waiting == null || waiting.isEmpty()) {
                continue;
            }

            List<Map<String, Object>> left = waiting.stream()
                    .filter(item -> !String.valueOf(actionId).equals(
                            String.valueOf(item.get("actionId"))))
                    .toList();

            if (left.size() != waiting.size()) {
                // Null rather than an empty list, so an emptied column reads the same as
                // one that never held anything.
                turn.setDeferred(left.isEmpty() ? null : left);
                turnRepository.saveAndFlush(turn);
            }
        }
    }

    /** The goal turn and every step of it. */
    private List<ConversationTurn> withGoal(Long goalTurnId) {
        List<ConversationTurn> turns = new java.util.ArrayList<>();

        turnRepository.findById(goalTurnId).ifPresent(turns::add);
        turns.addAll(turnRepository.findByGoalTurnIdOrderByIdAsc(goalTurnId));

        return turns;
    }

    @Override
    @Transactional(readOnly = true)
    public Proposal proposalOf(Long turnId) {
        ConversationTurn turn = turnId == null
                ? null
                : turnRepository.findById(turnId).orElse(null);

        // Every one of these is a way the id could be wrong, and each fails the same way:
        // a browser is telling us which turn to run, and it may name any number at all.
        boolean approvable = turn != null
                && awaitsApproval(turn)
                && turn.getConversation() != null
                && turn.getConversation().getOwner() != null
                && currentUserId().equals(turn.getConversation().getOwner().getId());

        if (!approvable) {
            throw new ResourceNotFoundException("Proposal", turnId);
        }

        return new Proposal(
                turn.getId(),
                // Null for a typed question held for approval: it is nobody's step but its
                // own, and linking it to a goal it is not part of would put it in that
                // goal's steps the next time one was planned.
                turn.getGoalTurn() == null ? null : turn.getGoalTurn().getId(),
                turn.getConversation().getConversationRef(),
                turn.getPrompt(),
                turn.getToolName(),
                turn.getStatement(),
                turn.getArguments(),
                turn.getActionId());
    }

    @Override
    @Transactional
    public void delete(String conversationRef) {
        conversationRepository.delete(mine(conversationRef));
    }

    @Override
    @Transactional
    public Thread thread(String conversationRef) {
        int limit = properties.getThreadLimit();

        if (conversationRef == null || conversationRef.isBlank() || limit <= 0) {
            return Thread.empty();
        }

        Conversation conversation = conversationRepository
                .findByConversationRefAndOwnerId(conversationRef, currentUserId())
                .orElse(null);

        if (conversation == null) {
            // Not the caller's, or gone. A new conversation starts either way, and it
            // should start without somebody else's questions in it.
            return Thread.empty();
        }

        List<ConversationTurn> answered = conversation.getTurns().stream()
                // A turn that failed has no answer to continue, and offering it as an
                // antecedent would invite the model to repeat the attempt.
                .filter(turn -> turn.getFailure() == null)
                .toList();

        // Counted after the failures are gone: taking the last `limit` of the unfiltered
        // list would drop good turns to make room for ones that were never sent.
        int windowStart = Math.max(0, answered.size() - limit);

        String summary = fold(conversation, answered.subList(0, windowStart));

        return new Thread(summary, answered.subList(windowStart, answered.size()).stream()
                .map(turn -> new McpServerClient.PriorTurn(
                        turn.getPrompt(), turn.getToolName(), turn.getStatement()))
                .toList());
    }

    /**
     * Folds the turns that have slid out of the window into the running summary.
     *
     * <p>Only the newly dropped ones go in, with the summary so far: re-reading the whole
     * conversation each time would make the thousandth question cost twenty times the
     * fiftieth, for an answer nobody is going to refer back to anyway.
     *
     * <p>Batched, because summarising on every turn past the window means a model call per
     * question for the oldest and least-consulted part of the conversation. Between batches
     * the unsummarised turns travel in neither half — which is the cost of the batch, and
     * why it is a small number.
     */
    private String fold(Conversation conversation, List<ConversationTurn> older) {
        String summary = conversation.getSummary() == null ? "" : conversation.getSummary();

        if (!properties.isSummarise() || older.isEmpty()) {
            return summary;
        }

        Long through = conversation.getSummarisedThroughId();

        List<ConversationTurn> pending = older.stream()
                .filter(turn -> through == null || turn.getId() > through)
                .toList();

        if (pending.size() < properties.getSummaryBatch()) {
            return summary;
        }

        String folded = mcpServerClient.summarise(summary, pending.stream()
                .map(turn -> new McpServerClient.PriorTurn(
                        turn.getPrompt(), turn.getToolName(), turn.getStatement()))
                .toList());

        if (folded == null || folded.equals(summary)) {
            // The MCP server was down, or said nothing usable. The turns stay unsummarised
            // and are offered again next time rather than being marked as folded in.
            return summary;
        }

        conversation.setSummary(folded);
        conversation.setSummarisedThroughId(pending.getLast().getId());
        conversationRepository.save(conversation);

        return folded;
    }

    /*
     * Its own transaction, deliberately. A prompt that threw rolls the caller's transaction
     * back, and a turn enlisted in that one would vanish along with it — losing exactly the
     * turns worth keeping, the ones that failed.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Recorded complete(Long turnId, McpServerClient.PromptResult result, String failure) {
        ConversationTurn turn = turnRepository.findById(turnId)
                .orElseThrow(() -> new ResourceNotFoundException("Turn", turnId));

        // The prompt and the goal it belongs to are the proposal's own and do not change:
        // what was approved is the sentence that was on screen. Everything else is the
        // outcome, and until now the proposal had none.
        turn.setToolName(result == null ? turn.getToolName() : result.toolName());
        turn.setStatus(result == null ? null : result.status());
        turn.setReasoning(result == null ? null : result.reasoning());
        turn.setProblem(problemOf(result));
        turn.setStatement(statementOf(result));
        turn.setAnswer(result == null ? null : blankToNull(result.answer()));
        turn.setWarnings(warningsOf(result));
        turn.setDeferred(deferredOf(result));
        turn.setRunRef(runRefOf(result));
        turn.setFailure(blankToNull(failure));
        turn.setExecuted(true);

        turnRepository.saveAndFlush(turn);

        // Touched so the sidebar moves it back to the top: approving is activity, and a
        // conversation whose timestamp stood still would sink out of sight while in use.
        Conversation conversation = turn.getConversation();
        conversationRepository.saveAndFlush(conversation);

        return new Recorded(conversation.getConversationRef(), turn.getId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Recorded record(String conversationRef, String prompt, String pinnedTool,
                           boolean executed, McpServerClient.PromptResult result,
                           String failure, Long goalTurnId) {

        Conversation conversation = conversationFor(conversationRef, prompt);

        ConversationTurn turn = ConversationTurn.builder()
                .conversation(conversation)
                .prompt(prompt)
                .pinnedTool(blankToNull(pinnedTool))
                .toolName(result == null ? null : result.toolName())
                .executed(executed)
                .status(result == null ? null : result.status())
                .reasoning(result == null ? null : result.reasoning())
                .problem(problemOf(result))
                .statement(statementOf(result))
                .answer(result == null ? null : blankToNull(result.answer()))
                .warnings(warningsOf(result))
                .deferred(deferredOf(result))
                .arguments(argumentsOf(result))
                .actionId(actionIdOf(result))
                .runRef(runRefOf(result))
                .failure(blankToNull(failure))
                .goalTurn(goalTurnId == null
                        ? null
                        : turnRepository.findById(goalTurnId).orElse(null))
                .build();

        conversation.getTurns().add(turn);

        // Saved through the conversation so that its updated_at moves with it: the list is
        // ordered by last activity, and a conversation whose timestamp stood still would
        // sink out of sight while it was being used.
        Conversation saved = conversationRepository.saveAndFlush(conversation);

        // The id is read back off the saved conversation, not off the object built above.
        //
        // A conversation loaded from the repository is already managed, so saving it merges
        // rather than persists: the cascade writes a *copy* of the turn and the instance
        // here never receives an id. Every turn after the first in a session came back with
        // turnId null, which is the identifier a caller needs to approve the step it just
        // proposed. Ordered by id, so the one just appended is the last.
        Long turnId = saved.getTurns().isEmpty()
                ? turn.getId()
                : saved.getTurns().getLast().getId();

        return new Recorded(saved.getConversationRef(), turnId);
    }

    /**
     * The conversation to append to.
     *
     * <p>An unknown reference starts a new conversation rather than failing. The alternative
     * is a console that throws away the question someone just typed because their session
     * had been deleted in another tab.
     */
    private Conversation conversationFor(String conversationRef, String prompt) {
        if (conversationRef != null && !conversationRef.isBlank()) {
            var existing = conversationRepository
                    .findByConversationRefAndOwnerId(conversationRef, currentUserId());

            if (existing.isPresent()) {
                return existing.get();
            }
            log.debug("Conversation {} is not the caller's; starting a new one", conversationRef);
        }

        User owner = userRepository.findById(currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserId()));

        return Conversation.builder()
                .conversationRef("conv_" + UUID.randomUUID().toString().replace("-", ""))
                .owner(owner)
                .title(title(prompt))
                .build();
    }

    /** The caller's conversation, or nothing. Someone else's is not found, not forbidden. */
    private Conversation mine(String conversationRef) {
        return conversationRepository
                .findByConversationRefAndOwnerId(conversationRef, currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationRef));
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId()
                .orElseThrow(() -> new IllegalStateException(
                        "A conversation belongs to a person; there is no authenticated caller"));
    }

    private ConversationResponse toResponse(Conversation conversation, boolean withTurns) {
        return new ConversationResponse(
                conversation.getConversationRef(),
                conversation.getTitle(),
                conversation.getTurns().size(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                goalProgress.isDeciding(conversation.getConversationRef()),
                withTurns
                        ? conversation.getTurns().stream().map(this::toResponse).toList()
                        : null);
    }

    /**
     * Whether this turn is a command somebody still has to say yes to.
     *
     * <p>Two turns arrive here. A step the goal loop wrote — nobody typed it, so it is
     * never run unattended. And a question somebody typed with execute ticked, against an
     * action the operator marked as needing approval — they asked for it to run, and the
     * action says a person sees the command first.
     *
     * <p>Both share the part that matters: a plan that came out well, and no run behind it.
     * A rejected plan is not something to approve, and one that already ran is not
     * something to approve again — that is a button that runs the command twice.
     */
    private static boolean awaitsApproval(ConversationTurn turn) {
        return turn.getRunRef() == null
                && "planned".equals(turn.getStatus())
                && (turn.getGoalTurn() != null || turn.isExecuted());
    }

    /**
     * The action the plan settled on.
     *
     * <p>The one that resolved to something, which is how the statement is found too: an
     * action the plan set aside carries no command, and the turn is about the one that did.
     *
     * <p>Kept so that approving re-plans the same action. A definition holds several — list,
     * create, update, delete — and the choice is made afresh every time; run again from the
     * sentence alone it came back as the listing, against a card that said DELETE.
     */
    private Long actionIdOf(McpServerClient.PromptResult result) {
        if (result == null || result.plan() == null
                || !(result.plan().get("actions") instanceof List<?> actions)) {
            return null;
        }

        return actions.stream()
                .filter(Map.class::isInstance)
                .map(action -> (Map<?, ?>) action)
                .filter(action -> {
                    Object resolved = action.get("resolved");
                    return resolved != null && !String.valueOf(resolved).isBlank();
                })
                .map(action -> action.get("action_id"))
                .filter(Objects::nonNull)
                .map(id -> Long.valueOf(String.valueOf(id)))
                .findFirst()
                .orElse(null);
    }

    /**
     * The values the plan actually ran on.
     *
     * <p>Taken from the result rather than from what the caller passed in: a value the loop
     * read out of an answer and one routing found in a typed sentence are the same fact by
     * the time a plan exists, and the plan is the thing being approved.
     *
     * <p>Stringified, because that is what goes back out as inputs. A number the MCP server
     * answered with is a number in JSON and a path segment in a URL.
     */
    private static Map<String, String> argumentsOf(McpServerClient.PromptResult result) {
        if (result == null || result.arguments() == null || result.arguments().isEmpty()) {
            return null;
        }

        Map<String, String> values = new java.util.LinkedHashMap<>();
        result.arguments().forEach((key, value) -> {
            if (key != null && value != null) {
                values.put(key, String.valueOf(value));
            }
        });

        return values.isEmpty() ? null : values;
    }

    private ConversationResponse.Turn toResponse(ConversationTurn turn) {
        return new ConversationResponse.Turn(
                turn.getId(),
                turn.getPrompt(),
                turn.getPinnedTool(),
                turn.getToolName(),
                turn.isExecuted(),
                turn.getStatus(),
                turn.getReasoning(),
                turn.getProblem(),
                turn.getStatement(),
                turn.getAnswer(),
                turn.getWarnings(),
                turn.getRunRef(),
                turn.getFailure(),
                awaitsApproval(turn),
                turn.getCreatedAt());
    }

    /**
     * What the plan resolved to.
     *
     * <p>Masked already — this is the plan's own text, the same string the console shows.
     * Kept on the turn rather than read back from the run, because a turn that was never
     * executed has no run, and "what would this have done" is the whole point of asking
     * without the box ticked.
     */
    private String statementOf(McpServerClient.PromptResult result) {
        if (result == null || result.plan() == null) {
            return null;
        }

        if (!(result.plan().get("actions") instanceof List<?> actions)) {
            return null;
        }

        return actions.stream()
                .filter(Map.class::isInstance)
                .map(action -> ((Map<?, ?>) action).get("resolved"))
                .map(String::valueOf)
                .filter(resolved -> !resolved.isBlank() && !"null".equals(resolved))
                .findFirst()
                .orElse(null);
    }

    /**
     * What the plan said it was narrowing on.
     *
     * <p>Read off the plan rather than recomputed: the check ran once, where the statement
     * was written and where the request was still in hand, and a second implementation here
     * would be a second thing to keep in step with it.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> warningsOf(McpServerClient.PromptResult result) {
        if (result == null || result.plan() == null) {
            return null;
        }

        if (!(result.plan().get("warnings") instanceof List<?> warnings) || warnings.isEmpty()) {
            // Null rather than an empty list: nothing to say and no row to read is the
            // same thing, and it keeps the column empty for the turns that had none.
            return null;
        }

        // Passed through as they arrive. Each is a code and the thing observed, never a
        // sentence — the wording belongs where the reader's language is known, and this
        // service does not know it.
        return warnings.stream()
                .filter(Map.class::isInstance)
                .map(warning -> (Map<String, Object>) warning)
                .toList();
    }

    /**
     * Why this turn produced nothing, from wherever the answer is.
     *
     * <p>The router's problem when there is one — "no published tool matches this request"
     * — and otherwise the plan's, which is where a step that could not be planned says so.
     *
     * <p>Only the router's used to be kept. A goal-loop step whose plan came back
     * incomplete recorded a turn with an empty problem, and because an unplanned step never
     * runs there is no result to wake the loop again: the goal stopped, the conversation
     * showed a blank card, and nothing anywhere said why.
     */
    @SuppressWarnings("unchecked")
    private String problemOf(McpServerClient.PromptResult result) {
        if (result == null) {
            return null;
        }

        if (result.problem() != null && !result.problem().isBlank()) {
            return result.problem();
        }

        if (result.plan() == null
                || !(result.plan().get("problems") instanceof List<?> problems)
                || problems.isEmpty()) {
            return null;
        }

        return problems.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining("; "));
    }

    /**
     * The actions the plan chose and could not run yet, with what each is waiting for.
     *
     * <p>Read off the plan for the same reason the warnings are: the decision was made
     * once, where the values in hand were known, and a second implementation here would be
     * a second thing to keep in step with it.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> deferredOf(McpServerClient.PromptResult result) {
        if (result == null || result.plan() == null) {
            return null;
        }

        if (!(result.plan().get("actions") instanceof List<?> actions)) {
            return null;
        }

        List<Map<String, Object>> waiting = actions.stream()
                .filter(Map.class::isInstance)
                .map(action -> (Map<String, Object>) action)
                .filter(action -> Boolean.TRUE.equals(action.get("skipped")))
                // Only the ones an earlier action has to answer for. An action the request
                // simply did not ask for is not pending; nothing will make it runnable.
                .filter(action -> String.valueOf(action.get("skip_reason")).startsWith("waiting on "))
                .map(action -> Map.of(
                        "actionId", action.get("action_id"),
                        "name", String.valueOf(action.getOrDefault("name", "")),
                        // The input names, not the sentence about them. Handed the
                        // sentence, the model wrote it back as its next step — "waiting on
                        // id values from search results" — which the planner then resolved
                        // as another search.
                        "waitingFor", inputNames(action),
                        "reason", String.valueOf(action.getOrDefault("skip_reason", ""))))
                .toList();

        // Null rather than an empty list: nothing pending and no row to read are the same
        // thing, and it keeps the column empty for the turns that had none.
        return waiting.isEmpty() ? null : waiting;
    }

    /** The inputs a deferred action is waiting for, as a list a person or a model reads. */
    private static String inputNames(Map<String, Object> action) {
        if (!(action.get("waiting_for") instanceof List<?> names) || names.isEmpty()) {
            return "";
        }

        return names.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /** The dispatched job, when there was one. A turn that only planned has no run. */
    private String runRefOf(McpServerClient.PromptResult result) {
        if (result == null || result.dispatch() == null) {
            return null;
        }

        Object runId = result.dispatch().get("run_id");
        return runId == null ? null : blankToNull(String.valueOf(runId));
    }

    private String title(String prompt) {
        String trimmed = prompt.strip();
        return trimmed.length() <= TITLE_LIMIT ? trimmed : trimmed.substring(0, TITLE_LIMIT) + "…";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() || "null".equals(value) ? null : value;
    }
}
