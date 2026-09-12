package com.mcpgateway.queue;

import com.mcpgateway.client.CipherClient;
import com.mcpgateway.domain.entity.Run;
import com.mcpgateway.domain.entity.RunTarget;
import com.mcpgateway.domain.enums.RunStatus;
import com.mcpgateway.domain.enums.TargetStatus;
import com.mcpgateway.domain.json.ActionConfig;
import com.mcpgateway.repository.RunRepository;
import com.mcpgateway.service.JsonRows;
import com.mcpgateway.service.RunOutputCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes one action's outcome into its run.
 *
 * <p>A separate bean from the listener, and not by preference: Spring's {@code @Transactional}
 * is applied by a proxy, so a transactional method called from another method of the same
 * class runs with no transaction at all. The listener also sleeps while waiting for a row
 * that is still being written, which is the last thing to do with a transaction open.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunResultRecorder {

    /** How much of a target's output is kept. Enough to see what happened, not a log store. */
    private static final int EXCERPT_LIMIT = 4000;

    /** How many rows are kept. A result set is a sample here, not a data warehouse. */
    private static final int ROW_LIMIT = 500;

    private final RunRepository runRepository;
    private final RunOutputCipher outputCipher;
    private final RunProgress progress;
    private final JsonRows jsonRows;

    @Transactional
    public void apply(ExecutionResult result, ExecutionResult.Outcome outcome, Long runId) {
        Run run = runRepository.findById(runId).orElse(null);

        if (run == null) {
            log.warn("Run {} disappeared before its result could be recorded", runId);
            return;
        }

        run.setStatus(runStatus(outcome.status()));
        run.setError(problemOf(outcome));

        if ("introspect".equals(run.getPurpose())) {
            storeSchema(run, outcome);
        }
        run.setFinishedAt(result.finishedAt() == null ? Instant.now() : result.finishedAt());

        if (run.getStartedAt() == null) {
            run.setStartedAt(result.startedAt());
        }

        // Replaced rather than appended: a redelivered result describes the same attempt,
        // and appending would double every target the second time round.
        run.getTargets().clear();

        for (ExecutionResult.Target target : outcome.targetsOrEmpty()) {
            RunTarget.RunTargetBuilder row = RunTarget.builder()
                    .run(run)
                    .address(target.host())
                    .status(targetStatus(target.status()))
                    .exitCode(target.exitCodeOrZero())
                    .durationMs(target.durationOrZero())
                    // The problem goes in with stderr, because that is where anyone looking
                    // for "why did this fail" looks first. A database that refused a
                    // connection prints nothing to stderr; its explanation is here and
                    // nowhere else, and dropping it left a row that said only "failed".
                    .stderrExcerpt(excerpt(
                            join(target.problem(), target.stderr()), target.wasTruncated()))
                    .finishedAt(run.getFinishedAt());

            storeOutput(row, target, "introspect".equals(run.getPurpose()), kindOf(run));
            run.getTargets().add(row.build());
        }

        runRepository.save(run);

        // The live view has been replaced by the record. It lingers rather than going now:
        // the poll that shows the last lines usually arrives just after the result does,
        // and clearing on the instant would blank the screen as the command finished.
        progress.finished(run.getRunRef());
    }

    /**
     * Puts a target's output where it belongs.
     *
     * <p>A schema read stays in the clear: what comes back is a list of tables and columns,
     * the planner has to read it back, and it is not anybody's data. Everything else is
     * sealed, because it is — a query returns whatever rows were asked for, and this table
     * had been quietly accumulating them.
     *
     * <p>Both shapes travel together in one envelope. The text is what a person reads and
     * the rows are what the panel draws a table from; neither can be derived from the other
     * safely, so both are kept, and one seal covers them.
     */
    private void storeOutput(RunTarget.RunTargetBuilder row, ExecutionResult.Target target,
                             boolean introspection, String kind) {

        // Read whole, then trimmed. The rows are taken from the full body because that is
        // the only moment it exists: what gets stored is an excerpt, and a JSON listing cut
        // at four thousand characters is not JSON at all — deriving the table afterwards
        // found nothing to derive it from.
        String full = outputOf(target, kind);

        String text = excerpt(full, target.wasTruncated());
        List<Map<String, Object>> rows = rowsOf(target, kind);

        // A REST answer is a table wearing a different coat. A database action already has
        // its rows; a command's output is a file somebody wants to read as a file.
        if (rows == null && "rest".equals(kind)) {
            rows = jsonRows.of(full);
        }

        if (introspection) {
            row.stdoutExcerpt(text).resultRows(rows);
            return;
        }

        RunOutputCipher.Output output = new RunOutputCipher.Output(text, rows);

        if (output.isEmpty()) {
            return;
        }

        try {
            CipherClient.Sealed sealed = outputCipher.seal(output);
            row.outputSealed(sealed.ciphertext()).outputKeyId(sealed.keyId());
        } catch (RuntimeException failure) {
            // Not stored in the clear as a fallback. Doing that would drop the protection
            // exactly when something is already wrong, and nobody would notice. The run
            // keeps its status and its statement; what it returned is gone, and says so.
            log.error("A run's output could not be sealed and was discarded: {}",
                    failure.getMessage());
            row.stdoutExcerpt("(the output could not be sealed and was not stored)");
        }
    }

    /** The rows to keep, capped so one query cannot fill the column. */
    private List<Map<String, Object>> rowsOf(ExecutionResult.Target target, String kind) {
        List<Map<String, Object>> rows = target.rowsOrEmpty();

        if (rows.isEmpty()) {
            // Null for a command, which has no rows at all; an empty list for a query that
            // matched nothing, which is a different thing and worth being able to say. The
            // kind decides it, not whether anything was printed: `ls` in an empty directory
            // prints nothing and still has no rows, and the empty list it used to get drew
            // "matched no rows" across the console where its output belonged.
            return "db".equals(kind) ? rows : null;
        }
        return rows.size() <= ROW_LIMIT ? rows : rows.subList(0, ROW_LIMIT);
    }

    /**
     * What a target actually produced.
     *
     * <p>A command prints to stdout; a query returns rows. Only the first was being kept,
     * so a database action ran, succeeded in nineteen milliseconds and stored nothing —
     * the row count was reported and the answer itself thrown away.
     */
    private String outputOf(ExecutionResult.Target target, String kind) {
        if (target.stdout() != null && !target.stdout().isBlank()) {
            return target.stdout();
        }

        // A command that printed nothing is not a query that matched nothing, and saying
        // "(no rows)" for `ls` in an empty directory reads as a fault in the tool rather
        // than as the answer. It succeeded, and this is what it said.
        if (!"db".equals(kind)) {
            return target.exitCodeOrZero() == 0 ? "(no output)" : null;
        }

        return renderRows(target);
    }

    /** The kind of action this run belongs to, or {@code null} when it no longer has one. */
    private static String kindOf(Run run) {
        return run.getAction() == null || run.getAction().getKind() == null
                ? null
                : run.getAction().getKind().name().toLowerCase();
    }

    /**
     * Lays rows out as text.
     *
     * <p>Text rather than a JSONB column: the run already has one place for "what came
     * back", every other kind of action puts its output there, and a second one would mean
     * the panel deciding which to show. Aligned columns because the reader is a person
     * looking at a report, not a program parsing it.
     */
    private String renderRows(ExecutionResult.Target target) {
        List<Map<String, Object>> rows = target.rowsOrEmpty();

        if (rows.isEmpty()) {
            // Distinguished from a query that was never run: an empty result is an answer,
            // and a blank cell reads as a failure nobody recorded.
            return target.rowCountOrZero() == 0 ? "(no rows)" : null;
        }

        List<String> columns = new ArrayList<>(rows.getFirst().keySet());
        int[] widths = new int[columns.size()];

        for (int index = 0; index < columns.size(); index++) {
            widths[index] = columns.get(index).length();
            for (Map<String, Object> row : rows) {
                widths[index] = Math.max(widths[index], cell(row.get(columns.get(index))).length());
            }
        }

        StringBuilder text = new StringBuilder();
        appendRow(text, columns, widths, columns::get);
        appendRow(text, columns, widths, index -> "-".repeat(widths[index]));

        for (Map<String, Object> row : rows) {
            appendRow(text, columns, widths, index -> cell(row.get(columns.get(index))));
        }

        text.append("\n(").append(rows.size()).append(" row(s))");
        return text.toString();
    }

    private void appendRow(StringBuilder text, List<String> columns, int[] widths,
                           java.util.function.IntFunction<String> value) {
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) {
                text.append("  ");
            }
            text.append(pad(value.apply(index), widths[index]));
        }
        text.append('\n');
    }

    private String pad(String value, int width) {
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }

    /** A null is written as such: an empty cell reads as an empty string, which it is not. */
    private String cell(Object value) {
        return value == null ? "NULL" : String.valueOf(value);
    }

    /**
     * Writes a read schema onto the action it describes.
     *
     * <p>Into {@code generatedSchema}, never over {@code schemaHint}: one is what the
     * database says and the other is what an operator wrote about what it means. Replacing
     * the second with the first would throw away the notes.
     *
     * <p>A failed introspection leaves the previous schema alone. An outage should not
     * empty out something that was correct an hour ago.
     */
    private void storeSchema(Run run, ExecutionResult.Outcome outcome) {
        if (!"succeeded".equalsIgnoreCase(outcome.status()) || run.getAction() == null) {
            return;
        }

        String schema = outcome.targetsOrEmpty().stream()
                .map(ExecutionResult.Target::stdout)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse(null);

        if (schema == null) {
            log.warn("Introspection for action {} returned nothing", run.getAction().getId());
            return;
        }

        ActionConfig config = run.getAction().getConfig();
        config.setGeneratedSchema(schema);
        config.setGeneratedSchemaAt(Instant.now().toString());

        // JSONB is replaced wholesale, so the document has to be set back for Hibernate to
        // notice it changed: mutating it in place leaves the entity looking untouched.
        run.getAction().setConfig(config);

        log.info("Schema stored for action {} ({} characters)",
                run.getAction().getId(), schema.length());
    }

    /**
     * The explanation for a failed action, wherever it happens to be.
     *
     * <p>An action can fail as a whole — a refused command, an unopenable credential — or on
     * one target while the action itself was fine. The outcome carries the first, each
     * target the second, and a row that recorded only the first said "failed" and nothing
     * more for the most common case there is.
     */
    private String problemOf(ExecutionResult.Outcome outcome) {
        if (outcome.problem() != null && !outcome.problem().isBlank()) {
            return outcome.problem();
        }

        return outcome.targetsOrEmpty().stream()
                .map(ExecutionResult.Target::problem)
                .filter(problem -> problem != null && !problem.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** Joins two pieces of explanation, skipping whichever is absent. */
    private String join(String problem, String output) {
        if (problem == null || problem.isBlank()) {
            return output;
        }
        return output == null || output.isBlank() ? problem : problem + "\n\n" + output;
    }

    /**
     * Trims output and says so.
     *
     * <p>The marker matters: without it a truncated command looks like a command that
     * printed exactly this much, and someone will read the last line as the end of the story.
     */
    private String excerpt(String output, boolean alreadyTruncated) {
        if (output == null || output.isEmpty()) {
            return null;
        }

        if (output.length() <= EXCERPT_LIMIT) {
            return alreadyTruncated ? output + "\n… (truncated by the executor)" : output;
        }
        return output.substring(0, EXCERPT_LIMIT) + "\n… (truncated)";
    }

    /**
     * Maps the executor's vocabulary onto this schema's.
     *
     * <p>{@code refused} has no counterpart in {@code run_status} and becomes {@code failed}:
     * from the operator's side the work did not happen, and inventing a status would mean a
     * migration for a distinction the {@code error} column already carries.
     */
    private RunStatus runStatus(String status) {
        return switch (status == null ? "" : status.toLowerCase(Locale.ROOT)) {
            case "succeeded" -> RunStatus.SUCCEEDED;
            case "cancelled" -> RunStatus.CANCELLED;
            default -> RunStatus.FAILED;
        };
    }

    private TargetStatus targetStatus(String status) {
        return switch (status == null ? "" : status.toLowerCase(Locale.ROOT)) {
            case "succeeded" -> TargetStatus.SUCCEEDED;
            case "cancelled" -> TargetStatus.SKIPPED;
            default -> TargetStatus.FAILED;
        };
    }

}
