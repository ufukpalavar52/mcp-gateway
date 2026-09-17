package com.mcpgateway.service;

import com.mcpgateway.client.McpServerClient;
import com.mcpgateway.service.intf.ConversationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a goal's next step runs on.
 *
 * <p>An unattended step may not route a sentence for values: a machine-written step carries
 * none by design, and asked to find one in "Processing the first user found with first_name
 * 'Yigit'" the model produced an id nobody had mentioned and sent a DELETE to approval
 * against it. These are a different thing — what the person typed, already resolved by the
 * planner and stored on the goal's own turn.
 */
class GoalLoopValuesTest {

    private static ConversationService.Goal goal(Map<String, String> arguments) {
        return new ConversationService.Goal(
                1L, "conv", "betigi /tmp/fib.py altina yaz ve calistir",
                "rock_linux_script", 7L, List.of(), List.of(), arguments);
    }

    private static McpServerClient.Step step(Map<String, String> values) {
        return new McpServerClient.Step(false, "calistir /tmp/fib.py", "", values);
    }

    /**
     * The path was given once and there is nowhere else to find it.
     *
     * <p>The command that writes the file prints nothing, so by the time the run step is
     * taken up no answer holds it, and the plan was refused for an input the person did
     * supply — "Missing required input(s): path".
     */
    @Test
    void aValueTheGoalWasTypedWithReachesTheNextStep() {
        Map<String, String> merged = GoalLoop.values(
                goal(Map.of("path", "/tmp/fib.py", "interpreter", "python3")),
                step(Map.of()));

        assertThat(merged).containsEntry("path", "/tmp/fib.py");
        assertThat(merged).containsEntry("interpreter", "python3");
    }

    /**
     * What a step read out of a result is the newer fact.
     *
     * <p>For a goal working through several records this is the only thing telling one from
     * the next: a goal's own value winning here would make every step repeat the first
     * record, which is the failure the whole deferral mechanism exists to prevent.
     */
    @Test
    void whatTheLoopReadWinsOverWhatTheGoalWasTypedWith() {
        Map<String, String> merged = GoalLoop.values(
                goal(Map.of("id", "69", "first_name", "Yigit")),
                step(Map.of("id", "86")));

        assertThat(merged).containsEntry("id", "86");
        assertThat(merged).containsEntry("first_name", "Yigit");
    }

    @Test
    void aGoalWithNoValuesChangesNothing() {
        assertThat(GoalLoop.values(goal(Map.of()), step(Map.of("id", "5"))))
                .isEqualTo(Map.of("id", "5"));
    }

    @Test
    void aGoalWhoseArgumentsWereNeverRecordedIsNoObstacle() {
        assertThat(GoalLoop.values(goal(null), step(Map.of("id", "5"))))
                .containsEntry("id", "5");
    }
}
