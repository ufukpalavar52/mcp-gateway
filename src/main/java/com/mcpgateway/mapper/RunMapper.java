package com.mcpgateway.mapper;

import com.mcpgateway.domain.entity.Run;
import com.mcpgateway.domain.entity.RunTarget;
import com.mcpgateway.domain.enums.TargetStatus;
import com.mcpgateway.dto.response.RunResponse;
import com.mcpgateway.queue.ExecutorPresence;
import com.mcpgateway.queue.RunProgress;
import com.mcpgateway.service.RunOutputCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Entity to response translation for runs. */
@Component
@RequiredArgsConstructor
public class RunMapper {

    /**
     * Long enough that a dispatch is never mistaken for a stall.
     *
     * <p>An executor reconnecting takes a second or two, and a job published into that
     * gap is picked up the moment it returns. Warning about those would train the reader
     * to ignore the warning, which costs more than the seconds it saves.
     */
    private static final Duration SETTLING = Duration.ofSeconds(20);

    private final RunOutputCipher outputCipher;
    private final RunProgress progress;
    private final ExecutorPresence executors;

    public RunResponse toResponse(Run run) {
        // A run that has not finished has no stored output yet — it is written once, when
        // the executor reports what happened. What it may have is a live view: the same
        // output arriving a second at a time while the command is still going.
        if (!finished(run)) {
            List<RunResponse.Target> live = watching(run);

            if (!live.isEmpty()) {
                return response(run, live);
            }
        }

        return response(run, run.getTargets().stream().map(this::toResponse).toList());
    }

    /** Whether the executor has reported on this run. */
    private static boolean finished(Run run) {
        return run.getFinishedAt() != null;
    }

    /**
     * Whether this run is waiting on an executor that is not there.
     *
     * <p>Three facts, and all three are needed. It has not finished; it was dispatched
     * long enough ago that a reconnect would have completed; and nothing is consuming the
     * job queue. The last is asked of the broker rather than guessed from silence — a
     * command can print nothing for ten minutes and be perfectly healthy, and time alone
     * cannot tell that apart from nobody listening.
     *
     * <p>{@code startedAt} is the dispatch for an unfinished run; the executor's real
     * start time overwrites it only when the result arrives.
     */
    private boolean stalled(Run run) {
        if (finished(run) || run.getStartedAt() == null) {
            return false;
        }

        if (run.getStartedAt().isAfter(Instant.now().minus(SETTLING))) {
            return false;
        }

        return !executors.listening();
    }

    /**
     * The live view as targets, so a caller draws it with the component it already has.
     *
     * <p>Not a second shape for the same thing. A watcher wants what the command has
     * printed so far, which is what a finished target carries too; giving it a field of its
     * own would mean every reader learning both.
     */
    private List<RunResponse.Target> watching(Run run) {
        return progress.of(run.getRunRef(), run.getActionRef()).stream()
                .map(live -> new RunResponse.Target(
                        live.host(),
                        // Running, whatever the row says: the row is written when the
                        // result arrives, and this is the time before that.
                        TargetStatus.RUNNING,
                        null,
                        null,
                        blankToNull(live.stdout()),
                        blankToNull(live.stderr()),
                        null))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private RunResponse response(Run run, List<RunResponse.Target> targets) {
        return new RunResponse(
                run.getId(),
                run.getRunRef(),
                run.getActionRef(),
                run.getDefinition() == null ? null : run.getDefinition().getId(),
                run.getDefinition() == null ? null : run.getDefinition().getToolName(),
                run.getAction() == null ? null : run.getAction().getName(),
                run.getActorLabel(),
                run.getPurpose(),
                run.getStatus(),
                run.getError(),
                run.getGeneratedQuery() == null
                        ? run.getGeneratedCommand()
                        : run.getGeneratedQuery(),
                run.getStartedAt(),
                run.getFinishedAt(),
                targets,
                // Filled in by the caller that knows about the whole job; one action knows
                // nothing about its siblings.
                null,
                stalled(run));
    }

    /**
     * One target's outcome, with its output opened if it was sealed.
     *
     * <p>The plaintext columns are still read, and not only for rows written before the
     * output was sealed: a schema read is stored in the clear on purpose. Falling back to
     * them is what lets both kinds be served by one screen.
     */
    private RunResponse.Target toResponse(RunTarget target) {
        RunOutputCipher.Output output = target.getOutputSealed() == null
                ? new RunOutputCipher.Output(target.getStdoutExcerpt(), target.getResultRows())
                : outputCipher.open(target.getOutputSealed(), target.getOutputKeyId());

        return new RunResponse.Target(
                target.getAddress(),
                target.getStatus(),
                target.getExitCode(),
                target.getDurationMs(),
                output.text(),
                target.getStderrExcerpt(),
                output.rows());
    }
}
