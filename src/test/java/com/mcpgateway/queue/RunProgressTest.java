package com.mcpgateway.queue;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The live view of a command that is still running.
 *
 * <p>`tail -f` used to show nothing at all: the executor waited for the command to finish,
 * and it never did. What is held here is the output arriving a second at a time — not a
 * record of anything, which is why none of it is written down.
 */
class RunProgressTest {

    private final RunProgress progress = new RunProgress();

    @Test
    void chunksAccumulateInOrder() {
        progress.append("run-1", "10.0.0.1", "first\n", null, false);
        progress.append("run-1", "10.0.0.1", "second\n", null, false);

        assertThat(progress.of("run-1")).singleElement().satisfies(live -> {
            assertThat(live.host()).isEqualTo("10.0.0.1");
            assertThat(live.stdout()).isEqualTo("first\nsecond\n");
            assertThat(live.done()).isFalse();
        });
    }

    @Test
    void eachHostIsItsOwnLog() {
        // A rolling command across three servers is three logs; interleaving them into one
        // stream would make each unreadable.
        progress.append("run-1", "10.0.0.1", "one\n", null, false);
        progress.append("run-1", "10.0.0.2", "two\n", null, false);

        assertThat(progress.of("run-1")).hasSize(2)
                .extracting(RunProgress.Live::stdout)
                .containsExactlyInAnyOrder("one\n", "two\n");
    }

    @Test
    void theEndOfAHostsOutputIsMarked() {
        // So a watcher can stop waiting without knowing the run's overall status.
        progress.append("run-1", "h", "done\n", null, true);

        assertThat(progress.of("run-1").getFirst().done()).isTrue();
    }

    @Test
    void stderrIsKeptApart() {
        progress.append("run-1", "h", "out", "err", false);

        assertThat(progress.of("run-1").getFirst().stdout()).isEqualTo("out");
        assertThat(progress.of("run-1").getFirst().stderr()).isEqualTo("err");
    }

    @Test
    void aRunNobodyReportedOnHasNoLiveView() {
        assertThat(progress.of("run-nothing")).isEmpty();
        assertThat(progress.of(null)).isEmpty();
    }

    @Test
    void theNewestLinesAreTheOnesKept() {
        /*
         * A log somebody is watching, so the lines they want are the ones that just
         * arrived. The stored output, which keeps the beginning, is the record — this is
         * the other end of the same output and it is bounded.
         */
        progress.append("run-1", "h", "x".repeat(70 * 1024), null, false);
        progress.append("run-1", "h", "THE-NEWEST", null, false);

        String held = progress.of("run-1").getFirst().stdout();

        assertThat(held).endsWith("THE-NEWEST");
        assertThat(held.length()).isLessThanOrEqualTo(64 * 1024);
    }

    @Test
    void aFinishedRunStillShowsItsLastLinesForAWhile() {
        /*
         * The poll that shows the last lines usually arrives just after the result does.
         * Clearing on the instant would blank the screen at the moment the command
         * finished, which reads as output having been lost.
         */
        progress.append("run-1", "h", "the last line\n", null, true);
        progress.finished("run-1");

        assertThat(progress.of("run-1")).singleElement()
                .satisfies(live -> assertThat(live.stdout()).isEqualTo("the last line\n"));
    }

    @Test
    void aChunkThatOmitsWhatIsFalseIsStillReadable() throws Exception {
        /*
         * Go's omitempty leaves out done and seq when they are zero, and Jackson gives a
         * record's missing component null — which a primitive cannot take. Every ordinary
         * chunk has done false and so omits it, so every ordinary chunk was refused: the
         * live view stayed empty and the only sign was a stack trace per second.
         */
        var mapper = new tools.jackson.databind.json.JsonMapper();

        var chunk = mapper.readValue(
                "{\"runId\":\"run-1\",\"host\":\"h\",\"stdout\":\"a line\"}",
                RunProgressListener.Chunk.class);

        assertThat(chunk.runId()).isEqualTo("run-1");
        assertThat(chunk.stdout()).isEqualTo("a line");
        assertThat(chunk.done()).isNull();

        new RunProgressListener(progress).onProgress(chunk);

        assertThat(progress.of("run-1")).singleElement()
                .satisfies(live -> {
                    assertThat(live.stdout()).isEqualTo("a line");
                    assertThat(live.done()).isFalse();
                });
    }

    @Test
    void aChunkWithoutARunIsIgnoredRatherThanStored() {
        progress.append(null, "h", "orphan", null, false);
        progress.append("", "h", "orphan", null, false);

        assertThat(progress.of("")).isEmpty();
    }
}
