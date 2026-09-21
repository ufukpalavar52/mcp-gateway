package com.mcpgateway.mapper;

import com.mcpgateway.domain.entity.Run;
import com.mcpgateway.domain.enums.RunStatus;
import com.mcpgateway.dto.response.RunResponse;
import com.mcpgateway.queue.ExecutorPresence;
import com.mcpgateway.queue.RunProgress;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A run nobody is going to finish.
 *
 * <p>A job is published and the run is marked running. If no executor is consuming the
 * queue the message waits, and the run stays running forever — nothing failed, so there
 * is no failure to report. The panel said "waiting for the result" for thirty-seven hours
 * and was telling the truth the whole time.
 *
 * <p>What makes this answerable is asking the broker rather than inferring from silence.
 * A command can print nothing for ten minutes and be perfectly healthy; time alone cannot
 * tell that apart from an empty queue.
 */
class StalledRunTest {

    private static final Duration SETTLED = Duration.ofMinutes(5);

    /** An executor presence that answers however the test needs, asking nothing. */
    private static ExecutorPresence presence(boolean listening) {
        return new ExecutorPresence(null) {
            @Override
            public boolean listening() {
                return listening;
            }
        };
    }

    private static RunMapper mapper(boolean listening) {
        return new RunMapper(null, new RunProgress(), presence(listening));
    }

    private static Run.RunBuilder dispatched(Duration ago) {
        return Run.builder()
                .runRef("run-1")
                .actionRef("act-1")
                .status(RunStatus.RUNNING)
                .startedAt(Instant.now().minus(ago));
    }

    @Test
    void aRunningJobWithNobodyOnTheQueueIsStalled() {
        RunResponse response = mapper(false).toResponse(dispatched(SETTLED).build());

        assertThat(response.stalled()).isTrue();

        // The status is left alone. This service observed no outcome, and writing one in
        // would be inventing the very thing the field exists to report honestly.
        assertThat(response.status()).isEqualTo(RunStatus.RUNNING);
    }

    @Test
    void aRunningJobWithAnExecutorListeningIsNot() {
        // The case that must not warn: a command that has printed nothing for five
        // minutes, on an executor that is there and working.
        assertThat(mapper(true).toResponse(dispatched(SETTLED).build()).stalled()).isFalse();
    }

    @Test
    void aJustDispatchedJobIsGivenTimeToBePickedUp() {
        // An executor reconnecting takes a second or two. Warning about that window would
        // teach the reader to ignore the warning.
        Run fresh = dispatched(Duration.ofSeconds(2)).build();

        assertThat(mapper(false).toResponse(fresh).stalled()).isFalse();
    }

    @Test
    void aFinishedRunIsNeverStalled() {
        Run done = dispatched(SETTLED)
                .status(RunStatus.SUCCEEDED)
                .finishedAt(Instant.now())
                .build();

        assertThat(mapper(false).toResponse(done).stalled()).isFalse();
    }

    @Test
    void aBrokerThatCannotBeAskedNeverAccusesAnybody() {
        // ExecutorPresence answers optimistically when the count fails, so this asserts
        // the mapper honours that rather than second-guessing it. Saying "this will never
        // finish" wrongly sends somebody hunting a fault that is not there.
        assertThat(mapper(true).toResponse(dispatched(Duration.ofHours(37)).build()).stalled())
                .isFalse();
    }
}
