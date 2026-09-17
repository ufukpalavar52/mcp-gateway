package com.mcpgateway.dto.response;

import com.mcpgateway.domain.enums.RunStatus;
import com.mcpgateway.domain.enums.TargetStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One dispatched action and what became of it.
 *
 * <p>{@code runRef} is the executor's identifier, and it is what a cancellation names — the
 * numeric id means nothing outside this database.
 */
public record RunResponse(Long id,
                          String runRef,
                          String actionRef,
                          Long definitionId,
                          String toolName,
                          String actionName,
                          String actorLabel,

                          /**
                           * Why this ran: {@code execute} for work somebody asked for,
                           * {@code introspect} for a schema this service went and read.
                           * Exposed so a history screen can tell them apart — they arrive
                           * on the same queue and otherwise look identical.
                           */
                          String purpose,
                          RunStatus status,
                          String error,
                          /**
                           * What this run was going to execute, masked as the plan showed
                           * it. For a dynamic action it is the only record of what the
                           * model wrote, and "failed" answers nothing without it.
                           */
                          String statement,
                          Instant startedAt,
                          Instant finishedAt,
                          List<Target> targets,

                          /**
                           * Every action of this job, when it carried more than one.
                           *
                           * <p>A job approved whole runs its actions under one reference:
                           * "write the script" and "run the script" are one decision and one
                           * job. Looking it up returned only the first, so the console showed
                           * the write — which prints nothing — and the output of the command
                           * that actually produced something was never on screen.
                           *
                           * <p>Null for a job with one action, which is most of them. The
                           * fields above stay the first action either way, so anything that
                           * only wants to show a result does not have to know about this.
                           */
                          List<RunResponse> steps) {

    public record Target(String address,
                         TargetStatus status,
                         Integer exitCode,
                         Integer durationMs,
                         String stdoutExcerpt,
                         String stderrExcerpt,

                         /** A query's rows, for a caller that can draw a table. */
                         List<Map<String, Object>> rows) {
    }
}
