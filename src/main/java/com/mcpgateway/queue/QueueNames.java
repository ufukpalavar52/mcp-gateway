package com.mcpgateway.queue;

/** Queue and exchange names shared with mcp-server and mcp-action. */
public final class QueueNames {

    /** Executable jobs, published by the planner and consumed by mcp-action. */
    public static final String ACTIONS = "mcp.actions";

    /** Outcomes, published by mcp-action and consumed here. */
    public static final String RESULTS = "mcp.results";

    /**
     * Cancellations. A fanout, because a cancel has to reach whichever executor holds the
     * run and nobody recorded which one that is.
     */
    public static final String CANCELLATIONS = "mcp.cancellations";

    /**
     * Partial output for runs that are still going, published by mcp-action.
     *
     * <p>A different kind of message from a result, and held differently. A result is the
     * record of what happened and must survive a broker restart; a chunk of a log somebody
     * is watching right now is worthless a minute later, and a queue of them accumulating
     * for nobody would be a slow leak with no reader.
     */
    public static final String PROGRESS = "mcp.progress";

    private QueueNames() {
    }
}
