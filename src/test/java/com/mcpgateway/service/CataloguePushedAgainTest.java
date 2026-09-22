package com.mcpgateway.service;

import com.mcpgateway.client.McpServerClient;
import com.mcpgateway.client.McpServerRefusedException;
import com.mcpgateway.domain.entity.Definition;
import com.mcpgateway.mapper.DefinitionMapper;
import com.mcpgateway.repository.DefinitionRepository;
import com.mcpgateway.repository.RunRepository;
import com.mcpgateway.repository.ToolCallRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.service.impl.ToolExecutionServiceImpl;
import com.mcpgateway.service.intf.AuditService;
import com.mcpgateway.service.intf.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The catalogue, pushed again when the MCP server has lost it.
 *
 * <p>The MCP server holds its catalogue in memory and fetches nothing — deliberately, so
 * that it holds no gateway credentials. The price is that a restart there leaves it
 * answering <em>Unknown tool: x. Publish the catalogue first.</em> to everything.
 *
 * <p>It was being told that in plain words and passing them to the user, twice in one day,
 * while holding both the definitions and the credentials to fix it.
 */
class CataloguePushedAgainTest {

    private static final String TOOL = "rock_linux_logs";

    private final McpServerClient client = mock(McpServerClient.class);
    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final DefinitionAccessGuard access = mock(DefinitionAccessGuard.class);
    private final DefinitionMapper mapper = mock(DefinitionMapper.class);

    private final ToolExecutionServiceImpl service = new ToolExecutionServiceImpl(
            client,
            definitions,
            access,
            mock(ToolCallRepository.class),
            mock(UserRepository.class),
            mapper,
            mock(AuditService.class),
            mock(RunRepository.class),
            mock(ConversationService.class));

    private McpServerClient.ExecutionResult invoke() {
        // id lives on BaseEntity, so it is set rather than built.
        Definition definition = Definition.builder().toolName(TOOL).build();
        definition.setId(1L);

        when(definitions.findByToolName(TOOL)).thenReturn(Optional.of(definition));
        when(access.mayRun(any())).thenReturn(true);
        when(definitions.findPublishedTools()).thenReturn(List.of(definition));

        return service.execute(TOOL, Map.of(), "", List.of());
    }

    private static McpServerRefusedException unknownTool() {
        return new McpServerRefusedException(
                HttpStatus.NOT_FOUND, "Unknown tool: " + TOOL + ". Publish the catalogue first.");
    }

    private static McpServerClient.ExecutionResult planned() {
        return new McpServerClient.ExecutionResult("planned", null, null);
    }

    @Test
    void anUnknownToolRepublishesTheCatalogueAndAsksAgain() {
        when(client.requestExecution(anyString(), any(), any(), any(), any()))
                .thenThrow(unknownTool())
                .thenReturn(planned());

        assertThat(invoke().status()).isEqualTo("planned");

        verify(client).publishCatalogue(anyList());
        verify(client, times(2)).requestExecution(anyString(), any(), any(), any(), any());
    }

    @Test
    void aToolThatIsStillUnknownAfterwardsIsReported() {
        // A second attempt would turn a clear answer into a loop. If a fresh catalogue does
        // not have it, the tool really is gone and saying so is the useful reply.
        when(client.requestExecution(anyString(), any(), any(), any(), any())).thenThrow(unknownTool());

        assertThatThrownBy(this::invoke)
                .isInstanceOf(McpServerRefusedException.class)
                .hasMessageContaining(TOOL);

        verify(client, times(1)).publishCatalogue(anyList());
        verify(client, times(2)).requestExecution(anyString(), any(), any(), any(), any());
    }

    @Test
    void anOrdinaryRefusalIsNotTreatedAsALostCatalogue() {
        // On the status, not the sentence — but only that status. A refusal the MCP server
        // meant is an answer, and republishing over it would hide it behind a retry.
        when(client.requestExecution(anyString(), any(), any(), any(), any()))
                .thenThrow(new McpServerRefusedException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "The plan was rejected"));

        assertThatThrownBy(this::invoke).isInstanceOf(McpServerRefusedException.class);

        verify(client, never()).publishCatalogue(anyList());
    }

    @Test
    void aCallThatWorksPublishesNothing() {
        when(client.requestExecution(anyString(), any(), any(), any(), any())).thenReturn(planned());

        assertThat(invoke().status()).isEqualTo("planned");

        verify(client, never()).publishCatalogue(anyList());
    }
}
