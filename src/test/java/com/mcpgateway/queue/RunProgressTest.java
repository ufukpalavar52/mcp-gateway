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

    /** One action, which is what every test here is about until the last two. */

    private static final String ACTION = "act-1";


    private final RunProgress progress = new RunProgress();

    @Test
    void chunksAccumulateInOrder() {
        progress.append("run-1", ACTION, "10.0.0.1", "first\n", null, false);
        progress.append("run-1", ACTION, "10.0.0.1", "second\n", null, false);

        assertThat(progress.of("run-1", ACTION)).singleElement().satisfies(live -> {
            assertThat(live.host()).isEqualTo("10.0.0.1");
            assertThat(live.stdout()).isEqualTo("first\nsecond\n");
            assertThat(live.done()).isFalse();
        });
    }

    @Test
    void eachHostIsItsOwnLog() {
        // A rolling command across three servers is three logs; interleaving them into one
        // stream would make each unreadable.
        progress.append("run-1", ACTION, "10.0.0.1", "one\n", null, false);
        progress.append("run-1", ACTION, "10.0.0.2", "two\n", null, false);

        assertThat(progress.of("run-1", ACTION)).hasSize(2)
                .extracting(RunProgress.Live::stdout)
                .containsExactlyInAnyOrder("one\n", "two\n");
    }

    @Test
    void theEndOfAHostsOutputIsMarked() {
        // So a watcher can stop waiting without knowing the run's overall status.
        progress.append("run-1", ACTION, "h", "done\n", null, true);

        assertThat(progress.of("run-1", ACTION).getFirst().done()).isTrue();
    }

    @Test
    void stderrIsKeptApart() {
        progress.append("run-1", ACTION, "h", "out", "err", false);

        assertThat(progress.of("run-1", ACTION).getFirst().stdout()).isEqualTo("out");
        assertThat(progress.of("run-1", ACTION).getFirst().stderr()).isEqualTo("err");
    }

    @Test
    void aRunNobodyReportedOnHasNoLiveView() {
        assertThat(progress.of("run-nothing", ACTION)).isEmpty();
        assertThat(progress.of(null, ACTION)).isEmpty();
    }

    @Test
    void theNewestLinesAreTheOnesKept() {
        /*
         * A log somebody is watching, so the lines they want are the ones that just
         * arrived. The stored output, which keeps the beginning, is the record — this is
         * the other end of the same output and it is bounded.
         */
        progress.append("run-1", ACTION, "h", "x".repeat(70 * 1024), null, false);
        progress.append("run-1", ACTION, "h", "THE-NEWEST", null, false);

        String held = progress.of("run-1", ACTION).getFirst().stdout();

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
        progress.append("run-1", ACTION, "h", "the last line\n", null, true);
        progress.finished("run-1");

        assertThat(progress.of("run-1", ACTION)).singleElement()
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

        // Asked for with no action, because the chunk named none. An executor that does not
        // say which action a chunk belongs to still gets a live view; it simply has one
        // buffer for the job, which is what every executor had before actions could share
        // a run.
        assertThat(progress.of("run-1", null)).singleElement()
                .satisfies(live -> {
                    assertThat(live.stdout()).isEqualTo("a line");
                    assertThat(live.done()).isFalse();
                });
    }

    @Test
    void aChunkWithoutARunIsIgnoredRatherThanStored() {
        progress.append(null, ACTION, "h", "orphan", null, false);
        progress.append("", ACTION, "h", "orphan", null, false);

        assertThat(progress.of("", ACTION)).isEmpty();
    }

    /**
     * Two actions of one job keep two logs.
     *
     * <p>Keyed by the job alone, they shared a buffer: a screen that draws every step then
     * drew the same output twice — the step that wrote a file displaying what the step
     * running it was printing, each box scrolled somewhere different, reading as two logs
     * that disagreed with each other.
     *
     * <p>The executor has always said which action a chunk belongs to. The gateway was
     * dropping it on the way in.
     */
    @Test
    void twoActionsOfOneRunDoNotShareABuffer() {
        progress.append("run-1", "act-write", "10.0.0.1", "wrote the file\n", null, false);
        progress.append("run-1", "act-run", "10.0.0.1", "line one\n", null, false);
        progress.append("run-1", "act-run", "10.0.0.1", "line two\n", null, false);

        assertThat(progress.of("run-1", "act-write")).singleElement()
                .satisfies(live -> assertThat(live.stdout()).isEqualTo("wrote the file\n"));

        assertThat(progress.of("run-1", "act-run")).singleElement()
                .satisfies(live -> assertThat(live.stdout()).isEqualTo("line one\nline two\n"));
    }

    @Test
    void oneActionAcrossTwoHostsStillReadsAsTwoLogs() {
        progress.append("run-1", "act-1", "10.0.0.1", "first\n", null, false);
        progress.append("run-1", "act-1", "10.0.0.2", "second\n", null, false);

        assertThat(progress.of("run-1", "act-1")).hasSize(2)
                .extracting(RunProgress.Live::host)
                .containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2");
    }
}
