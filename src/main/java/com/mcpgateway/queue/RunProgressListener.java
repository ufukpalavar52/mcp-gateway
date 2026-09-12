package com.mcpgateway.queue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Takes partial output off the queue and puts it where a poll can find it.
 *
 * <p>Nothing here writes to the database. A chunk is not a record of anything — the result
 * that follows is, and it carries the whole output — so this feeds the live view and
 * nothing else. That also means a chunk that arrives for a run this service has never heard
 * of is simply kept: it will be asked for by reference or it will expire.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunProgressListener {

    private final RunProgress progress;

    @RabbitListener(queues = QueueNames.PROGRESS)
    public void onProgress(Chunk chunk) {
        if (chunk == null || chunk.runId() == null) {
            return;
        }

        progress.append(chunk.runId(), chunk.host(), chunk.stdout(), chunk.stderr(),
                Boolean.TRUE.equals(chunk.done()));
    }

    /**
     * One chunk of a running command's output.
     *
     * <p>Unknown fields are ignored so mcp-action can add to the message without this
     * refusing every chunk until both sides are deployed — which for a live view would mean
     * a blank screen rather than a visible failure.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chunk(String runId,
                        String actionRunId,
                        String host,

                        /**
                         * Boxed, both of them, because the publisher leaves out what is
                         * zero or false.
                         *
                         * <p>Go's {@code omitempty} does that, and Jackson gives a record's
                         * missing component null — which a primitive cannot take. Every
                         * ordinary chunk has {@code done} false and so omits it, so every
                         * ordinary chunk was refused: the live view stayed empty and the
                         * only sign was a stack trace per second in the log.
                         */
                        Integer seq,
                        String stdout,
                        String stderr,
                        Boolean done) {
    }
}
