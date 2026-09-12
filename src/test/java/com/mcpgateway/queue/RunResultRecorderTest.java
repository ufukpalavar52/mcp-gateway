package com.mcpgateway.queue;

import com.mcpgateway.client.CipherClient;
import com.mcpgateway.domain.entity.Action;
import com.mcpgateway.domain.entity.Run;
import com.mcpgateway.domain.enums.ActionKind;
import com.mcpgateway.repository.RunRepository;
import com.mcpgateway.service.RunOutputCipher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What a database action leaves behind.
 *
 * <p>A query's answer is its rows, and they arrive in their own field rather than on
 * stdout. Nothing read that field, so a report ran, succeeded, recorded a row count and
 * stored none of the rows — the one thing the person who asked for it wanted.
 *
 * <p>The output no longer lands in the row in the clear: it is sealed, and what these
 * tests read is the envelope on its way to the cipher. Same assertions, one layer out.
 */
class RunResultRecorderTest {

    private final RunRepository runs = mock(RunRepository.class);
    private final RunOutputCipher cipher = mock(RunOutputCipher.class);
    private final RunProgress progress = new RunProgress();
    private final RunResultRecorder recorder =
            new RunResultRecorder(runs, cipher, progress, new com.mcpgateway.service.JsonRows());

    /** What was handed to the cipher, which is where a run's output goes now. */
    private RunOutputCipher.Output sealed;

    private Run recording() {
        return recording(ActionKind.DB);
    }

    /**
     * A run of one kind, because what "nothing came back" means depends on it.
     *
     * <p>A query that matched nothing has no rows; a command that printed nothing has no
     * output. Saying "(no rows)" for `ls` in an empty directory reads as a fault in the
     * tool rather than as the answer it is.
     */
    private Run recording(ActionKind kind) {
        Run run = Run.builder()
                .id(1L)
                .action(Action.builder().kind(kind).build())
                .build();
        when(runs.findById(1L)).thenReturn(Optional.of(run));
        when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cipher.seal(any())).thenAnswer(invocation -> {
            sealed = invocation.getArgument(0);
            return new CipherClient.Sealed(new byte[]{1}, "v1");
        });
        return run;
    }

    @Test
    void rowsAreKeptAsReadableOutput() {
        recording();

        ExecutionResult.Target returned = target(List.of(
                Map.of("id", 7, "name", "nightly"),
                Map.of("id", 8, "name", "hourly")));
        recorder.apply(result(returned), outcome(returned), 1L);

        assertThat(sealed.text()).contains("id", "name", "nightly", "hourly");
        assertThat(sealed.text()).contains("(2 row(s))");
        assertThat(sealed.rows()).hasSize(2);
    }

    @Test
    void nothingOfTheOutputIsLeftInTheClear() {
        /*
         * The reason this changed. A query about accounts had been writing whole rows —
         * name, email, identifiers — into result_rows, and the same rows into
         * stdout_excerpt as aligned text, where they stayed indefinitely.
         */
        Run run = recording();

        ExecutionResult.Target returned = target(List.of(Map.of("tckn", "12345678901")));
        recorder.apply(result(returned), outcome(returned), 1L);

        var stored = run.getTargets().getFirst();

        assertThat(stored.getStdoutExcerpt()).isNull();
        assertThat(stored.getResultRows()).isNull();
        assertThat(stored.getOutputSealed()).isNotNull();
        assertThat(stored.getOutputKeyId()).isEqualTo("v1");
    }

    @Test
    void anUnsealableOutputIsDroppedRatherThanWrittenInTheClear() {
        // The fallback that must not exist: writing plaintext when the cipher is down
        // would drop the protection exactly when something is already wrong.
        Run run = Run.builder().id(1L).build();
        when(runs.findById(1L)).thenReturn(Optional.of(run));
        when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cipher.seal(any())).thenThrow(new IllegalStateException("cipher unreachable"));

        ExecutionResult.Target returned = target(List.of(Map.of("email", "someone@example.com")));
        recorder.apply(result(returned), outcome(returned), 1L);

        var stored = run.getTargets().getFirst();

        assertThat(stored.getResultRows()).isNull();
        assertThat(stored.getOutputSealed()).isNull();
        assertThat(stored.getStdoutExcerpt())
                .doesNotContain("someone@example.com")
                .contains("could not be sealed");
    }

    @Test
    void aSchemaReadStaysInTheClear() {
        // Table definitions, not data — and the planner reads them back out of this column.
        Run run = Run.builder().id(1L).purpose("introspect").build();
        when(runs.findById(1L)).thenReturn(Optional.of(run));
        when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExecutionResult.Target read = new ExecutionResult.Target(
                "127.0.0.1", "succeeded", 0, "tblAccounts(id, email)", null, null, null, null,
                null, 12L);
        recorder.apply(result(read), outcome(read), 1L);

        assertThat(run.getTargets().getFirst().getStdoutExcerpt())
                .isEqualTo("tblAccounts(id, email)");
        assertThat(run.getTargets().getFirst().getOutputSealed()).isNull();
    }

    @Test
    void aNullCellSaysSoRatherThanLookingEmpty() {
        recording();

        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("finished_at", null);

        ExecutionResult.Target returned = target(List.of(row));
        recorder.apply(result(returned), outcome(returned), 1L);

        assertThat(sealed.text()).contains("NULL");
    }

    @Test
    void aQueryThatMatchedNothingSaysSo() {
        // An empty result is an answer. A blank cell reads as a failure nobody recorded.
        recording();

        ExecutionResult.Target returned = target(List.of());
        recorder.apply(result(returned), outcome(returned), 1L);

        assertThat(sealed.text()).isEqualTo("(no rows)");
    }

    @Test
    void aCommandThatPrintedNothingSaysSoInItsOwnWords() {
        // `ls` in an empty directory. It worked, and "(no rows)" — a phrase about queries —
        // reads as the tool having failed to fetch something.
        recording(ActionKind.SSH);

        ExecutionResult.Target quiet = new ExecutionResult.Target(
                "127.0.0.1", "succeeded", 0, null, null, null, null, null, null, 4L);
        recorder.apply(result(quiet), outcome(quiet), 1L);

        assertThat(sealed.text()).isEqualTo("(no output)");

        // No rows at all, not "no rows matched". The empty list it used to get drew
        // "matched no rows" across the console where the output belonged.
        assertThat(sealed.rows()).isNull();
    }

    @Test
    void aCommandThatFailedSilentlyStoresNothingRatherThanClaimingItRan() {
        // A non-zero exit with nothing on stdout has not "returned no output"; whatever
        // went wrong is in stderr, and this column asserting success would contradict it.
        recording(ActionKind.SSH);

        ExecutionResult.Target failed = new ExecutionResult.Target(
                "127.0.0.1", "failed", 2, null, null, null, null, null, null, 4L);
        recorder.apply(result(failed), outcome(failed), 1L);

        assertThat(sealed).isNull();
    }

    @Test
    void aCommandsOutputTravelsWithNoRowsBesideIt() {
        /*
         * The seal used to turn a null rows list into an empty one, because Map.of refuses
         * null. That erased the only distinction the field carries — no rows *at all*,
         * which is what a command has, against no rows *matched*, which is a query's
         * answer — and the console, seeing an empty array, drew "matched no rows" over the
         * top of every command's output.
         */
        recording(ActionKind.SSH);

        ExecutionResult.Target printed = new ExecutionResult.Target(
                "127.0.0.1", "succeeded", 0, "active (running) since Mon",
                null, null, null, null, null, 9L);
        recorder.apply(result(printed), outcome(printed), 1L);

        assertThat(sealed.text()).contains("active (running)");
        assertThat(sealed.rows()).isNull();
    }

    @Test
    void aRestAnswerIsReadAsRowsBeforeItIsTrimmed() {
        /*
         * The rows come from the full body because that is the only moment it exists: what
         * gets stored is an excerpt, and a JSON listing cut at four thousand characters is
         * not JSON at all. Deriving the table when somebody asked found nothing to derive
         * it from — ninety-nine users came back as a paragraph.
         */
        recording(ActionKind.REST);

        String body = "{\"count\": 2, \"data\": ["
                + "{\"id\": 1, \"name\": \"" + "a".repeat(5000) + "\"},"
                + "{\"id\": 2, \"name\": \"veli\"}]}";

        ExecutionResult.Target answered = new ExecutionResult.Target(
                "http://h/users", "succeeded", 200, body, null, null, null, null, null, 9L);
        recorder.apply(result(answered), outcome(answered), 1L);

        assertThat(sealed.rows()).hasSize(2);
        assertThat(sealed.rows().get(1)).containsEntry("name", "veli");

        // The text is still the excerpt, and still says it was cut.
        assertThat(sealed.text()).endsWith("… (truncated)");
    }

    @Test
    void aCommandThatPrintsJsonIsStillReadAsText() {
        // `cat config.json` is a file somebody wants to read as a file; a table would hide
        // the formatting they were looking at.
        recording(ActionKind.SSH);

        ExecutionResult.Target printed = new ExecutionResult.Target(
                "127.0.0.1", "succeeded", 0, "[{\"a\": 1}]", null, null, null, null, null, 4L);
        recorder.apply(result(printed), outcome(printed), 1L);

        assertThat(sealed.rows()).isNull();
        assertThat(sealed.text()).isEqualTo("[{\"a\": 1}]");
    }

    @Test
    void stdoutStillWinsForACommand() {
        // An SSH action prints; it has no rows, and nothing about this changes it.
        recording();

        ExecutionResult.Target printed = new ExecutionResult.Target(
                "web-01", "succeeded", 0, "active (running)", null, null, null, null, null, 12L);

        recorder.apply(new ExecutionResult("run-1", "succeeded",
                List.of(outcome(printed)),
                null, Instant.now(), Instant.now(), "test"), outcome(printed), 1L);

        assertThat(sealed.text()).isEqualTo("active (running)");
    }

    private ExecutionResult.Target target(List<Map<String, Object>> rows) {
        return new ExecutionResult.Target(
                "127.0.0.1", "succeeded", 0, null, null, null, rows.size(), rows, null, 19L);
    }

    private ExecutionResult result(ExecutionResult.Target target) {
        return new ExecutionResult("run-1", "succeeded", List.of(outcome(target)),
                null, Instant.now(), Instant.now(), "test");
    }

    private ExecutionResult.Outcome outcome(ExecutionResult.Target target) {
        return new ExecutionResult.Outcome("act-1", 7L, "succeeded", List.of(target), null, 15L);
    }
}
