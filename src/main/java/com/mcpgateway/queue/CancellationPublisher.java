package com.mcpgateway.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Asks whichever executor is holding a run to stop.
 *
 * <p>Broadcast, not addressed. The broker chose which executor got the job and nobody
 * recorded that choice, so a cancellation goes to every one of them and each checks whether
 * it holds that run id. An executor that does not is unaffected, which costs nothing.
 *
 * <p>Publishing is a request, not an outcome. Whether the run actually stopped arrives the
 * usual way — as a result on {@code mcp.results} with status {@code cancelled} — and this
 * service does not pretend otherwise by marking the row itself.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancellationPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void cancel(String runRef, String actor, String reason) {
        rabbitTemplate.convertAndSend(QueueNames.CANCELLATIONS, "",
                new Cancellation(runRef, actor, reason));

        log.info("Cancellation broadcast for run {} by {}", runRef, actor);
    }

    /** Matches mcp-action's {@code queue.Cancel}. */
    public record Cancellation(String runId, String actor, String reason) {
    }
}
