package com.mcpgateway.queue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading what mcp-action actually sends.
 *
 * <p>The Go side marks its numbers and flags {@code omitempty}, so a zero or a false is
 * simply absent from the JSON. Jackson builds a record through its canonical constructor
 * and hands {@code null} for an absent component — which a primitive cannot take, and the
 * whole message is rejected with "Cannot map null into type boolean". Not hypothetical:
 * it rejected every result until these components were boxed.
 */
class ExecutionResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void aResultWithEveryOptionalFieldOmittedIsRead() {
        String json = """
                {
                  "runId": "run-1",
                  "status": "succeeded",
                  "actions": [
                    {
                      "actionRunId": "act-1",
                      "actionId": 7,
                      "status": "succeeded",
                      "targets": [
                        {"host": "web-01", "status": "succeeded", "durationMs": 12}
                      ],
                      "durationMs": 15
                    }
                  ],
                  "startedAt": "2026-08-27T12:00:00Z",
                  "finishedAt": "2026-08-27T12:00:01Z",
                  "executor": "test"
                }
                """;

        ExecutionResult result = mapper.readValue(json, ExecutionResult.class);

        assertThat(result.runId()).isEqualTo("run-1");
        assertThat(result.actions()).hasSize(1);

        ExecutionResult.Target target = result.actions().getFirst().targets().getFirst();
        assertThat(target.host()).isEqualTo("web-01");
        // Absent in the JSON, and read back as the zero the sender meant.
        assertThat(target.exitCodeOrZero()).isZero();
        assertThat(target.wasTruncated()).isFalse();
    }

    @Test
    void anOutcomeWithNoTargetsIsRead() {
        // A refused action never reaches a host, so it reports none.
        String json = """
                {
                  "runId": "run-2",
                  "status": "refused",
                  "actions": [
                    {"actionRunId": "act-1", "actionId": 7, "status": "refused",
                     "problem": "command contains a blocked pattern"}
                  ],
                  "executor": "test"
                }
                """;

        ExecutionResult result = mapper.readValue(json, ExecutionResult.class);
        ExecutionResult.Outcome outcome = result.actions().getFirst();

        assertThat(outcome.targetsOrEmpty()).isEmpty();
        assertThat(outcome.durationOrZero()).isZero();
        assertThat(outcome.problem()).contains("blocked pattern");
    }

    @Test
    void anUnknownFieldFromANewerExecutorIsIgnored() {
        // So a newer executor can add a field without this service refusing every message
        // until it is redeployed.
        String json = """
                {"runId": "run-3", "status": "succeeded", "actions": [],
                 "executor": "test", "somethingNew": {"a": 1}}
                """;

        assertThat(mapper.readValue(json, ExecutionResult.class).runId()).isEqualTo("run-3");
    }
}
