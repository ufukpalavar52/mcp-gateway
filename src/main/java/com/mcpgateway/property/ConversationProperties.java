package com.mcpgateway.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How much of a console conversation travels with the next question.
 *
 * <p>Central rather than compiled in, because it is a judgement about a model and a budget
 * rather than about this service: a larger context window or a cheaper model changes the
 * right answer, and neither is a reason to rebuild the gateway.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "mcp.conversation")
public class ConversationProperties {

    /**
     * How many recent turns are sent word for word.
     *
     * <p>A follow-up nearly always continues the last thing said, so the window matters
     * far more than the depth. Everything older is folded into a summary instead of being
     * dropped — the point at which that starts is exactly this number.
     */
    private int threadLimit = 50;

    /**
     * How many turns are folded into the summary at a time.
     *
     * <p>Summarising on every single turn past the window would mean a model call per
     * question for something almost nobody refers back to. Batching trades a little
     * staleness at the far end of a long conversation for not paying that.
     */
    private int summaryBatch = 10;

    /** Whether anything is summarised at all. Off leaves old turns simply out of reach. */
    private boolean summarise = true;

    /**
     * How many steps one goal may take, including the first.
     *
     * <p>Counted in code rather than asked of the model. A loop that asks whether to
     * continue can be told yes forever; one that counts cannot, and this is the only
     * stopping rule that does not depend on the model agreeing to stop.
     */
    private int goalSteps = 5;

    /**
     * Whether a goal may take more than one step at all.
     *
     * <p>Read-only work only, whatever this says: a loop that chooses its own next command
     * against real machines is a different proposition from one that chooses its own next
     * query, and the second is what this is.
     */
    private boolean goalLoop = true;
}
