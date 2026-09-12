package com.mcpgateway.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which conversations the goal loop is thinking about right now.
 *
 * <p>Deciding what comes after a finished run is a model call, and on a real goal it took
 * eleven seconds. The console had nothing to show for that: the result appeared, then
 * nothing, then an approval card. It looked frozen, and the first attempt at fixing it was
 * wired to {@code conversation_turns.deferred} — a column that turned out never to be
 * written once the selection began choosing one action at a time. The indicator was
 * inert, which is worse than absent: it looked done.
 *
 * <p>So the fact is taken from the only thing that actually knows it. The loop marks a
 * conversation before it asks and clears it when it has an answer, and the panel reads
 * that.
 *
 * <p>In memory, like {@link com.mcpgateway.queue.RunProgress} and for the same reason:
 * this is true for a few seconds and then it is not. Nothing here is a record. Behind two
 * gateways a poll may reach the instance that is not deciding, and the failure is an
 * indicator that does not appear — the step still arrives.
 */
@Component
public class GoalProgress {

    /** Long enough to cover a slow model, short enough that a crash cannot leave a spinner. */
    private static final Duration STALE = Duration.ofMinutes(2);

    private final Map<String, Instant> deciding = new ConcurrentHashMap<>();

    public void deciding(String conversationRef) {
        if (conversationRef != null) {
            deciding.put(conversationRef, Instant.now());
        }
    }

    public void settled(String conversationRef) {
        if (conversationRef != null) {
            deciding.remove(conversationRef);
        }
    }

    /**
     * Whether a step is being decided for this conversation.
     *
     * <p>Anything older than {@link #STALE} is treated as finished and forgotten. A loop
     * that died between marking and clearing would otherwise leave the console claiming
     * work that stopped, and a spinner nobody can dismiss is its own kind of wrong.
     */
    public boolean isDeciding(String conversationRef) {
        Instant since = conversationRef == null ? null : deciding.get(conversationRef);

        if (since == null) {
            return false;
        }
        if (since.isBefore(Instant.now().minus(STALE))) {
            deciding.remove(conversationRef);
            return false;
        }
        return true;
    }
}
