package com.mcpgateway.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Whether anybody is listening for the work this service publishes.
 *
 * <p>A job is published to {@code mcp.actions} and the run is marked running. If no
 * executor is consuming that queue the message simply waits, and the run stays running
 * for as long as the installation lives — there is no failure anywhere to report, because
 * nothing failed. Nothing happened.
 *
 * <p>That is not hypothetical. On 19 September an executor started before its config
 * server was ready, fell back to a broker address that inside a container points at
 * itself, and never asked again. It sat there for thirty-seven hours with the queue
 * empty of consumers, and the screen said "waiting for the result" the whole time —
 * true, and useless. Finding it took reading container logs, because every service
 * reported itself as running.
 *
 * <p>The broker already knows the answer and will give it for the asking: a consumer
 * count on the queue. No new message, no column, no agreement with the executor about a
 * heartbeat it might also fail to send.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorPresence {

    /**
     * How long one answer is reused.
     *
     * <p>A history page maps fifty runs in a loop and every one of them would otherwise
     * ask the broker the same question. Short enough that a reader refreshing a stuck run
     * sees the truth almost at once, long enough that one screen is one question.
     */
    private static final Duration FRESH = Duration.ofSeconds(5);

    private final RabbitTemplate rabbitTemplate;

    private final AtomicReference<Answer> last = new AtomicReference<>();

    private record Answer(boolean listening, Instant asOf) {
    }

    /** Whether at least one executor is consuming the job queue. */
    public boolean listening() {
        Answer cached = last.get();
        if (cached != null && cached.asOf().isAfter(Instant.now().minus(FRESH))) {
            return cached.listening();
        }

        boolean listening = ask();
        last.set(new Answer(listening, Instant.now()));
        return listening;
    }

    /**
     * Asks the broker, and answers yes to anything it cannot determine.
     *
     * <p>Deliberately optimistic on failure. This is read to decide whether to tell
     * somebody their run will never finish, and saying that wrongly — because the broker
     * was briefly unreachable, or the queue had not been declared yet — is worse than not
     * saying it: it sends them to look for a fault that is not there, which is the exact
     * cost this field exists to avoid.
     */
    private boolean ask() {
        try {
            Long consumers = rabbitTemplate.execute(
                    channel -> channel.consumerCount(QueueNames.ACTIONS));

            return consumers == null || consumers > 0;
        } catch (Exception failure) {
            log.debug("Could not count consumers on {}: {}",
                    QueueNames.ACTIONS, failure.getMessage());
            return true;
        }
    }
}
