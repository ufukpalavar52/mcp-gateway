package com.mcpgateway.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A console session as the panel reads it.
 *
 * <p>{@code turns} is null in a listing and populated when one conversation is opened. A
 * list is for choosing which conversation to return to, and sending every turn of every
 * one of them would move the whole history to draw a sidebar.
 */
public record ConversationResponse(String conversationRef,
                                   String title,
                                   int turnCount,
                                   Instant createdAt,
                                   Instant updatedAt,

                                   /**
                                    * Whether the goal loop is deciding a next step for this
                                    * conversation right now.
                                    *
                                    * <p>Two model calls long, and the console showed nothing
                                    * for either: a result, then a silence, then an approval
                                    * card. Taken from the loop itself, which is the only
                                    * thing that knows.
                                    */
                                   boolean continuing,

                                   List<Turn> turns) {

    /**
     * One question and what it turned into.
     *
     * <p>No output here, by design: {@code runRef} names the run that holds it, and the
     * panel fetches that. The alternative was a second copy of every result set.
     */
    public record Turn(Long id,
                       String prompt,
                       String pinnedTool,
                       String toolName,
                       boolean executed,
                       String status,
                       String reasoning,
                       String problem,
                       String statement,

                       /**
                        * Every command on this card, when it carried more than one.
                        *
                        * <p>Null for the usual single-command turn, and for every turn
                        * recorded before a card could hold several — {@code statement} still
                        * holds the first either way, so nothing that only wants to show
                        * something has to know about this.
                        */
                       List<String> statements,

                       String answer,
                       List<Map<String, Object>> warnings,
                       String runRef,
                       String failure,

                       /**
                        * Whether this turn is a step the goal loop wrote and did not run.
                        *
                        * <p>Derived rather than stored: it is exactly "part of a goal, not
                        * executed, no run behind it". Sending the parts and letting the
                        * panel work it out would put the rule in two places, and the copy
                        * in the browser is the one that drifts.
                        */
                       boolean awaitingApproval,

                       /**
                        * What was asked, for a step the goal loop wrote.
                        *
                        * <p>Null for a turn somebody typed — it is its own goal and the
                        * prompt is already on the card.
                        *
                        * <p>A conversation can hold more than one goal at once: leave a
                        * step unapproved, ask for something else, come back and approve it,
                        * and the first goal carries on from where it stopped. Without this
                        * the card says only "approve this command" and gives no way to tell
                        * which of two requests it belongs to — which reads as the console
                        * going back to something already finished.
                        */
                       String goalPrompt,

                       Instant createdAt) {
    }
}
