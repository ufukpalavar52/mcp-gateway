package com.mcpgateway.service.intf;

import com.mcpgateway.client.McpServerClient;
import com.mcpgateway.common.dto.PageResponse;
import java.util.List;
import java.util.Map;
import com.mcpgateway.dto.response.ConversationResponse;
import org.springframework.data.domain.Pageable;

/**
 * Console sessions, kept so that leaving the page does not end one.
 *
 * <p>Every method here is scoped to the caller. There is no "find by ref" that ignores who
 * is asking, because there is no caller in this service that should have one.
 */
public interface ConversationService {

    /**
     * The caller's own conversations, newest activity first. Without their turns.
     *
     * @param search words to look for in the title and in the questions asked; blank means
     *               everything. Searching the questions and not only the title is the
     *               point: a title is the first thing that was asked, and what somebody
     *               looks for a week later is usually a table or a host mentioned halfway
     *               down.
     */
    PageResponse<ConversationResponse> findMine(String search, Pageable pageable);

    /** One of the caller's conversations, with every turn in it. */
    ConversationResponse open(String conversationRef);

    /** Removes one of the caller's conversations and its turns. */
    void delete(String conversationRef);

    /**
     * What the model is told about this conversation before the next question.
     *
     * <p>The window is capped: a follow-up nearly always continues the last thing said, and
     * sending a whole thread would push the catalogue out of the model's attention to
     * answer a question about the previous sentence. Everything older is folded into a
     * summary rather than dropped, so a long conversation keeps its beginning.
     *
     * <p>Empty for a conversation that is not the caller's, rather than an error. Starting
     * a new thread is what happens next either way, and it should start without somebody
     * else's questions in it.
     */
    Thread thread(String conversationRef);

    /** A goal and the steps taken for it, oldest first. Empty when there is no such turn. */
    Goal goalOf(String runRef);

    /**
     * A step the loop wrote and did not run, ready to be approved.
     *
     * <p>The turn id arrives from a browser, so what it names is checked rather than
     * trusted: the turn must be in one of the caller's own conversations, must be a step of
     * a goal, and must not have run already. Without those checks, approving would take any
     * turn id and run it — including a step of somebody else's goal, whose earlier output
     * would then be opened to plan the one after.
     *
     * @throws com.mcpgateway.common.exception.ResourceNotFoundException when it is none of those
     */
    Proposal proposalOf(Long turnId);

    /**
     * Turns down a step that was waiting for approval.
     *
     * <p>The other half of being asked. A screen that can only say yes is not asking; it is
     * waiting for somebody to give in, and the proposal sits there being offered until they
     * do.
     *
     * <p>The turn keeps what it was going to do — the sentence and the command are the
     * record of what was declined — and stops being a proposal. The goal it belongs to
     * stops offering that action too: turning a step down is a decision about the goal, not
     * a request to be asked again in a minute.
     *
     * @throws com.mcpgateway.common.exception.ResourceNotFoundException when the turn is
     *         not one of the caller's, or was never waiting
     */
    void decline(Long turnId);

    /**
     * Marks a deferred action as taken up, so the goal stops offering it.
     *
     * <p>Clearing the entry is the bookkeeping that keeps the loop from proposing the same
     * step forever: what is left in a turn's `deferred` is what nothing has answered for.
     */
    void takeUp(Long goalTurnId, Object actionId);

    /** A step waiting for a person to say yes. */
    record Proposal(Long turnId, Long goalTurnId, String conversationRef,
                    String request, String toolName, String statement,

                    /**
                     * The values it was planned with.
                     *
                     * <p>Carried through the approval so the re-plan starts from what was
                     * shown. Without them the only input was the turn's sentence, and a
                     * step the loop wrote has no values in its sentence — routing supplied
                     * one of its own, it did not match the command on screen, and the
                     * dispatch was stopped by {@code expect}. The card went back to waiting
                     * and nothing said why.
                     */
                    java.util.Map<String, String> arguments,

                    /**
                     * The action it was planned as.
                     *
                     * <p>A definition holds several and the choice is made afresh on every
                     * plan. Re-planned from the sentence alone, an approved DELETE came back
                     * as the listing action, and {@code expect} refused the dispatch. What a
                     * person approved is a command, not a sentence.
                     */
                    Long actionId) {

        public java.util.Map<String, String> arguments() {
            return arguments == null ? java.util.Map.of() : arguments;
        }
    }

    /**
     * Fills a proposal in with what happened when it was approved.
     *
     * <p>The same turn, updated — not a second one beside it. Recording the approval as a
     * new turn left the proposal exactly as it was: still planned, still with no run behind
     * it, so still offering to be approved. The button stayed on screen and every press ran
     * the command again.
     *
     * @return the turn, so the caller can answer with the same shape an ordinary prompt does
     */
    Recorded complete(Long turnId, McpServerClient.PromptResult result, String failure);

    /**
     * What one prompt has become so far.
     *
     * @param owner the person whose conversation this is, so work continued on their
     *              behalf is attributed to them rather than to the process
     */
    record Goal(Long turnId,
                String conversationRef,
                String request,
                String toolName,
                Long owner,
                List<Step> steps,

                /**
                 * Actions the plan chose and could not run yet.
                 *
                 * <p>The difference between asking a model whether there is more to do and
                 * reading that there is. Empty when the goal's steps left nothing pending.
                 */
                List<Map<String, Object>> pending) {

        /** One step of a goal: what was asked, and where its answer can be read. */
        public record Step(String request, String statement, String runRef,
                           String failure) {
        }
    }

    /**
     * What a model is told about a conversation before the next question.
     *
     * <p>Two parts because they age differently: the window is what a follow-up refers to,
     * word for word, and the summary is everything that has already slid past it.
     */
    record Thread(String summary, List<McpServerClient.PriorTurn> turns) {

        public static Thread empty() {
            return new Thread("", List.of());
        }
    }

    /**
     * Writes a question and its answer into a conversation, starting one if needed.
     *
     * @param conversationRef the conversation to append to; null or unknown starts a new one
     * @param failure         why the request never reached an answer, when it did not. A
     *                        failed turn is still part of the conversation
     * @param goalTurnId      the turn whose goal this one is a step of, or null. Set when
     *                        the turn is made rather than patched afterwards: a link
     *                        applied later depends on an id coming back, and when it did
     *                        not the step was quietly recorded as a goal of its own
     * @return the conversation it went into and the turn it became
     */
    Recorded record(String conversationRef,
                    String prompt,
                    String pinnedTool,
                    boolean executed,
                    McpServerClient.PromptResult result,
                    String failure,
                    Long goalTurnId);

    /** Where a turn was written down. */
    record Recorded(String conversationRef, Long turnId) {
    }
}
