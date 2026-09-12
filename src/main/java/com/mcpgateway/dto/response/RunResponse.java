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
                          List<Target> targets) {

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
