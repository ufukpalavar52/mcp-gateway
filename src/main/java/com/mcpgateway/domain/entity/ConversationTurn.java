package com.mcpgateway.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

/**
 * One question and what it turned into.
 *
 * <p>Everything here is the conversation itself: what was asked, which tool answered, why,
 * and what it would run. The output is deliberately absent — {@link #runRef} points at the
 * run that holds it. Two copies of a result set would mean two places to look when personal
 * data has to be found or removed, and the second copy would be the one nobody remembers.
 */
@Entity
@Table(name = "conversation_turns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTurn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /**
     * The turn whose goal this one is a step of. Null when this turn is the goal.
     *
     * <p>A step is a turn rather than a row in a table of its own, because that is what it
     * already looks like: the operator asked for two things, saw two answers, and typing
     * the second question themselves produced exactly the same screen. Two structures
     * drawn identically would be two things to keep in step for no gain.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_turn_id")
    private ConversationTurn goalTurn;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    /**
     * The tool the operator pinned, if they pinned one.
     *
     * <p>Separate from {@link #toolName}, which is what actually answered. Whether a person
     * chose the tool or the model did is the interesting part of a routing decision, and one
     * column could not say both.
     */
    @Column(name = "pinned_tool")
    private String pinnedTool;

    @Column(name = "tool_name")
    private String toolName;

    @Column(nullable = false)
    @Builder.Default
    private boolean executed = false;

    /** {@code planned}, {@code incomplete}, {@code rejected}, or null when nothing matched. */
    @Column
    private String status;

    @Column(columnDefinition = "text")
    private String reasoning;

    @Column(columnDefinition = "text")
    private String problem;

    /** What the plan resolved to, masked as the plan showed it. */
    @Column(columnDefinition = "text")
    private String statement;

    /**
     * What the plan narrowed on that nobody asked for.
     *
     * <p>Kept because a warning is worth more later than at the time. Whether "493" counted
     * everything is not a question the person watching it appear asks; it is the question
     * somebody asks a week afterwards, by which time the warning had scrolled away.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> warnings;

    /**
     * Actions the plan chose but could not run yet, and what each is waiting for.
     *
     * <p>A fact the plan knew and the turn used to lose. "Find the user called Mehmet and
     * delete them" chooses two actions and the second cannot run: the id it deletes by is
     * in the first one's answer, which does not exist at planning time.
     *
     * <p>Without this the loop had to ask a model whether there was more to do, and was
     * twice told "done" by a reason that described the deletion it had not performed.
     * Recorded, it is read rather than guessed at.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> deferred;

    /**
     * Every command this turn put in front of somebody, when there was more than one.
     *
     * <p>Null for a turn that showed one, which is most of them and every turn recorded
     * before this column existed — {@code statement} still holds the first either way, so
     * nothing that reads the history has to know about this.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> statements;

    /** The actions those commands belong to, in the same order. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Long> actionIds;

    /**
     * The values this turn was planned with.
     *
     * <p>Approving re-plans rather than replays — a stored command is the one path into the
     * executor that skips every check — and the only thing going into that re-plan was the
     * turn's sentence. A step the loop wrote has no values in its sentence: asked to act on
     * "the first user found with first_name 'Yigit'", routing produced an id of its own,
     * which did not match the command that had been shown, so {@code expect} stopped the
     * dispatch and the card fell back to waiting. Nothing wrong ran; nothing ran at all.
     *
     * <p>Remembered rather than re-derived. The re-plan goes through the same guardrails,
     * from the same values, and arrives at the same command.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> arguments;

    /**
     * The action this turn was planned as.
     *
     * <p>The other half of remembering the plan. A definition carries several actions —
     * list, create, update, delete — and which one runs is chosen afresh on every plan.
     * Re-planning an approval ran that choice again and picked the listing: the screen said
     * DELETE, the re-plan said GET, and {@code expect} refused it.
     *
     * <p>What was approved is a particular command, not whichever action fits the sentence
     * this time.
     */
    @Column(name = "action_id")
    private Long actionId;

    /** The run this turn dispatched, if any. A pointer, never a copy of its output. */
    @Column(name = "run_ref")
    private String runRef;

    /**
     * What the model itself said, when no tool was the right thing to call.
     *
     * <p>Separate from {@link #statement}, and it has to be: one is what a system ran, the
     * other is what a model said. Kept in one column they would be shown the same way, and
     * a guess about how many accounts there are would read exactly like a count.
     */
    @Column(columnDefinition = "text")
    private String answer;

    /**
     * Why the request never reached an answer.
     *
     * <p>A failed turn is still part of the conversation: "the MCP server was down when I
     * asked this" is exactly what someone coming back an hour later needs to see, and a
     * history that quietly dropped those would look like the question was never asked.
     */
    @Column(columnDefinition = "text")
    private String failure;
}
