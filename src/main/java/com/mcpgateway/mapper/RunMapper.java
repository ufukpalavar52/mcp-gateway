package com.mcpgateway.mapper;

import com.mcpgateway.domain.entity.Run;
import com.mcpgateway.domain.entity.RunTarget;
import com.mcpgateway.domain.enums.TargetStatus;
import com.mcpgateway.dto.response.RunResponse;
import com.mcpgateway.queue.RunProgress;
import com.mcpgateway.service.RunOutputCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Entity to response translation for runs. */
@Component
@RequiredArgsConstructor
public class RunMapper {

    private final RunOutputCipher outputCipher;
    private final RunProgress progress;

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
                null);
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
