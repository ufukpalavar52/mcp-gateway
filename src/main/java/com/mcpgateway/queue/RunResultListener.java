package com.mcpgateway.queue;

import com.mcpgateway.domain.entity.Run;
import com.mcpgateway.repository.RunRepository;
import com.mcpgateway.service.GoalLoop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Writes down what an executor did.
 *
 * <p>This service owns the database, so it is the one that records outcomes; mcp-action has
 * no credentials for one and no reason to hold any. The listener is the other half of the
 * loop that dispatch opened — without it a job runs and its result sits in a queue forever,
 * which is exactly what was happening before this existed.
 *
 * <p>Excerpts, not transcripts. The executor already caps its output at 256 KB per target;
 * this trims further, because a row that holds a log file makes every query over the table
 * slower for the sake of output nobody reads from a database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunResultListener {


    private final RunRepository runRepository;
    private final RunResultRecorder recorder;
    private final GoalLoop goalLoop;

    /**
     * How long to wait for a run row that has not appeared yet.
     *
     * <p>There is a real race here, not a theoretical one. The job is published by the
     * planner, so an executor can start — and finish — before this service has committed the
     * rows describing it: a REST action against a local endpoint takes about 25 ms, and the
     * transaction that records it is still open. Measured on this stack, the result arrived
     * roughly 13 ms before the row was visible.
     *
     * <p>Waiting is done outside the transaction, and briefly. Anything longer than this and
     * the row is genuinely absent rather than late.
     */
    private static final int LOOKUP_ATTEMPTS = 10;
    private static final long LOOKUP_DELAY_MS = 200;

    @RabbitListener(queues = QueueNames.RESULTS)
    public void onResult(ExecutionResult result) {
        if (result == null || result.actions() == null) {
            log.warn("Discarding a result with no actions");
            return;
        }

        log.info("Result for run {} from {}: {}",
                result.runId(), result.executor(), result.status());

        for (ExecutionResult.Outcome outcome : result.actions()) {
            Run run = awaitRun(outcome.actionRunId());

            if (run == null) {
                // Not worth failing the message over: a result whose row never appears means
                // the definition was deleted mid-flight, or this is a redelivery after the
                // row was cleaned up. Rejecting it would have the broker send it back forever.
                log.warn("No run recorded for action {}; discarding its result",
                        outcome.actionRunId());
                continue;
            }

            recorder.apply(result, outcome, run.getId());

            // After the result is written, not before: the next step is decided from what
            // this one returned, and a decision taken against a half-written row would be
            // made on the previous answer.
            //
            // Never at the cost of the result. A failure here used to reject the message,
            // which had the broker redeliver it, which ran the loop again — the result was
            // already safely recorded and the redelivery achieved nothing but repetition.
            try {
                runRepository.findWithActionById(run.getId()).ifPresent(goalLoop::advance);
            } catch (RuntimeException failure) {
                log.warn("The goal loop could not advance after run {}: {}",
                        outcome.actionRunId(), failure.getMessage());
            }
        }
    }

    /**
     * Finds the run, allowing for one that is still being written.
     *
     * <p>Deliberately not inside the listener's transaction: sleeping with a transaction
     * open would hold a connection for the duration and, worse, would never see a row
     * committed after the read began.
     */
    private Run awaitRun(String actionRef) {
        for (int attempt = 0; attempt < LOOKUP_ATTEMPTS; attempt++) {
            Run found = runRepository.findByActionRef(actionRef).orElse(null);
            if (found != null) {
                return found;
            }

            try {
                Thread.sleep(LOOKUP_DELAY_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

}
