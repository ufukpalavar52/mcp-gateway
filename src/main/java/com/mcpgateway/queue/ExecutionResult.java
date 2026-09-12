package com.mcpgateway.queue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What mcp-action reports for one job.
 *
 * <p>Mirrors that service's {@code job.Result}. Kept as a separate type rather than shared
 * through a module: the two are written in different languages and released separately, and
 * a shape that has to be agreed is easier to see when it is written down twice.
 *
 * <p>Unknown fields are ignored so a newer executor can add one without this service
 * refusing every message until it is redeployed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutionResult(String runId,
                              String status,
                              List<Outcome> actions,
                              String problem,
                              Instant startedAt,
                              Instant finishedAt,
                              String executor) {

    /*
     * Every number and flag is boxed, deliberately.
     *
     * The Go side marks these `omitempty`, so a zero or a false is simply absent from the
     * JSON. Jackson builds a record through its canonical constructor and hands `null` for
     * an absent component — which a primitive cannot take, and the whole message is
     * rejected. Boxing makes absence representable; the accessors below turn it back into
     * the zero the sender meant.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Outcome(String actionRunId,
                          Long actionId,
                          String status,
                          List<Target> targets,
                          String problem,
                          Long durationMs) {

        public long durationOrZero() {
            return durationMs == null ? 0L : durationMs;
        }

        public List<Target> targetsOrEmpty() {
            return targets == null ? List.of() : targets;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Target(String host,
                         String status,
                         Integer exitCode,
                         String stdout,
                         String stderr,
                         Boolean truncated,
                         Integer rowCount,
                         List<Map<String, Object>> rows,
                         String problem,
                         Long durationMs) {

        public int rowCountOrZero() {
            return rowCount == null ? 0 : rowCount;
        }

        public List<Map<String, Object>> rowsOrEmpty() {
            return rows == null ? List.of() : rows;
        }

        public int exitCodeOrZero() {
            return exitCode == null ? 0 : exitCode;
        }

        public boolean wasTruncated() {
            return Boolean.TRUE.equals(truncated);
        }

        public int durationOrZero() {
            return durationMs == null ? 0 : durationMs.intValue();
        }
    }
}
