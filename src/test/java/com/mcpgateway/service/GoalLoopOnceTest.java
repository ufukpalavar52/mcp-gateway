package com.mcpgateway.service;

import com.mcpgateway.client.McpServerClient;
import com.mcpgateway.domain.entity.Action;
import com.mcpgateway.domain.entity.Run;
import com.mcpgateway.domain.enums.ActionKind;
import com.mcpgateway.domain.enums.RunStatus;
import com.mcpgateway.property.ConversationProperties;
import com.mcpgateway.repository.RunRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.service.intf.ConversationService;
import com.mcpgateway.service.intf.ToolExecutionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The goal advances once per job, not once per action.
 *
 * <p>A job approved whole carries several actions under one reference, and every one of
 * them arrives here as it finishes. Evaluated twice, the same goal did not have to give the
 * same answer — and it did not. The loop decided the goal was met, was asked again, and the
 * second time proposed running the script it had just run. The step after that turned the
 * model's own "the goal has been met" into a command and asked for approval to echo it.
 *
 * <p>Three approvals for one decision, each more absurd than the last.
 */
class GoalLoopOnceTest {

    private final ConversationService conversations = mock(ConversationService.class);
    private final ToolExecutionService executions = mock(ToolExecutionService.class);
    private final McpServerClient mcpServer = mock(McpServerClient.class);
    private final RunRepository runs = mock(RunRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RunOutputCipher cipher = mock(RunOutputCipher.class);
    private final ConversationProperties properties = new ConversationProperties();

    private final GoalLoop loop = new GoalLoop(conversations, executions, mcpServer, runs,
            users, cipher, properties, new GoalProgress());

    private static Run run(RunStatus status) {
        Action action = new Action();
        action.setKind(ActionKind.SSH);

        Run run = new Run();
        run.setRunRef("job-1");
        run.setPurpose("execute");
        run.setStatus(status);
        run.setAction(action);
        return run;
    }

    @Test
    void aJobWhoseOtherActionIsStillRunningDoesNotAdvance() {
        Run first = run(RunStatus.SUCCEEDED);
        when(runs.findByRunRefOrderByIdAsc("job-1"))
                .thenReturn(List.of(first, run(RunStatus.RUNNING)));

        loop.advance(first);

        // Not even asked: the goal is not settled, so there is nothing to decide yet.
        verify(conversations, never()).goalOf(any());
    }

    @Test
    void theActionThatFinishesLastDoesAdvance() {
        Run last = run(RunStatus.SUCCEEDED);
        when(runs.findByRunRefOrderByIdAsc("job-1"))
                .thenReturn(List.of(run(RunStatus.SUCCEEDED), last));

        loop.advance(last);

        verify(conversations).goalOf("job-1");
    }

    /**
     * A cancelled or failed sibling is settled too.
     *
     * <p>Waiting for it to succeed would wait forever, and a goal whose job is over — badly
     * or not — is a goal somebody should be told about rather than one left hanging.
     */
    @Test
    void aSiblingThatFailedCountsAsSettled() {
        Run last = run(RunStatus.SUCCEEDED);
        when(runs.findByRunRefOrderByIdAsc("job-1"))
                .thenReturn(List.of(run(RunStatus.FAILED), last));

        loop.advance(last);

        verify(conversations).goalOf("job-1");
    }
}
