package com.mcpgateway.service;

import com.mcpgateway.domain.entity.Action;
import com.mcpgateway.domain.entity.Definition;
import com.mcpgateway.domain.json.ActionConfig;
import com.mcpgateway.mapper.DefinitionMapper;
import com.mcpgateway.repository.AiModelRepository;
import com.mcpgateway.repository.DefinitionRepository;
import com.mcpgateway.repository.HostGroupRepository;
import com.mcpgateway.repository.SecretRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.service.impl.DefinitionServiceImpl;
import com.mcpgateway.service.intf.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A secret belongs to whoever is still using it.
 *
 * <p>Secrets have no owner: an action references one by id from inside its JSON, and one SSH
 * key is quite properly shared by every definition that reaches the same host. Three of them
 * shared one here. Deleting any of the three took the key with it, and the other two broke at
 * their next run saying the key was missing — with nothing to connect that to a deletion
 * somebody had made days earlier.
 *
 * <p>Sharing was never the mistake. The deletion was.
 */
class SharedSecretTest {

    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final SecretRepository secrets = mock(SecretRepository.class);

    // Deletion is an edit, so the guard is consulted on the way in. These tests are about
    // which secrets survive, not about who may delete: an administrator, and on we go.
    private final DefinitionAccessGuard guard = mock(DefinitionAccessGuard.class);

    private final DefinitionServiceImpl service = new DefinitionServiceImpl(
            definitions, mock(AiModelRepository.class), mock(HostGroupRepository.class),
            mock(UserRepository.class), mock(DefinitionMapper.class), mock(AuditService.class),
            secrets, mock(com.mcpgateway.client.CipherClient.class),
            mock(ApplicationEventPublisher.class), guard,
            mock(com.mcpgateway.repository.DefinitionPermissionRepository.class));

    /** A definition whose one SSH action holds `secretId` as its private key. */
    private static Definition definition(Long id, Long secretId) {
        ActionConfig config = new ActionConfig();
        config.setPrivateKeySecretId(secretId);

        Action action = new Action();
        action.setConfig(config);

        Definition definition = new Definition();
        definition.setId(id);
        definition.setToolName("tool_" + id);
        definition.getActions().add(action);
        return definition;
    }

    @SuppressWarnings("unchecked")
    private List<Long> deletedSecrets() {
        ArgumentCaptor<Iterable<Long>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(secrets).deleteAllById(captor.capture());

        List<Long> ids = new java.util.ArrayList<>();
        captor.getValue().forEach(ids::add);
        return ids;
    }

    @org.junit.jupiter.api.BeforeEach
    void asAnAdministrator() {
        when(guard.mayEdit(any())).thenReturn(true);
    }

    @Test
    void aSecretAnotherDefinitionStillUsesSurvives() {
        Definition going = definition(1L, 13L);
        when(definitions.findWithActionsById(1L)).thenReturn(Optional.of(going));
        when(definitions.findAll()).thenReturn(List.of(going, definition(2L, 13L)));

        service.delete(1L);

        // Nothing to delete, so nothing is asked for: a call with an empty list would be
        // harmless and a call with id 13 in it would break the definition next door.
        verify(secrets, never()).deleteAllById(any());
    }

    @Test
    void aSecretNobodyElseUsesGoes() {
        Definition going = definition(1L, 13L);
        when(definitions.findWithActionsById(1L)).thenReturn(Optional.of(going));
        when(definitions.findAll()).thenReturn(List.of(going, definition(2L, 99L)));

        service.delete(1L);

        assertThat(deletedSecrets()).containsExactly(13L);
    }

    @Test
    void theDefinitionBeingDeletedDoesNotCountAsSomebodyElse() {
        /*
         * It is in findAll() until the delete lands, and counting it would mean a definition
         * always kept its own secrets — leaving rows nothing can reach and nobody would think
         * to look for.
         */
        Definition going = definition(1L, 13L);
        when(definitions.findWithActionsById(1L)).thenReturn(Optional.of(going));
        when(definitions.findAll()).thenReturn(List.of(going));

        service.delete(1L);

        assertThat(deletedSecrets()).containsExactly(13L);
    }
}
