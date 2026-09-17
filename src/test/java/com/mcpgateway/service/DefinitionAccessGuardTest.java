package com.mcpgateway.service;

import com.mcpgateway.domain.entity.Definition;
import com.mcpgateway.domain.entity.DefinitionPermission;
import com.mcpgateway.domain.enums.DefinitionAccess;
import com.mcpgateway.domain.enums.UserRole;
import com.mcpgateway.repository.DefinitionPermissionRepository;
import com.mcpgateway.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Who may run a definition, and who may change one.
 *
 * <p>Until this existed, authorisation was the role alone: anybody with DEVELOPER could run
 * every published tool, including the one carrying {@code allowedCommands: ["*"]} and sudo.
 */
class DefinitionAccessGuardTest {

    private static final Long ME = 7L;

    private final DefinitionPermissionRepository permissions =
            mock(DefinitionPermissionRepository.class);
    private final DefinitionAccessGuard guard = new DefinitionAccessGuard(permissions);

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private void signedInAs(UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(ME, "me@example.com", role, "t"), null, List.of()));
    }

    private static Definition definition(DefinitionAccess access) {
        Definition definition = new Definition();
        definition.setId(1L);
        definition.setAccess(access);
        return definition;
    }

    private void named(boolean canRun, boolean canEdit) {
        when(permissions.findByDefinitionIdAndUserId(1L, ME)).thenReturn(Optional.of(
                DefinitionPermission.builder().canRun(canRun).canEdit(canEdit).build()));
    }

    @Test
    void anOpenDefinitionIsRunnableByAnybody() {
        signedInAs(UserRole.VIEWER);

        assertThat(guard.mayRun(definition(DefinitionAccess.OPEN))).isTrue();
    }

    @Test
    void aRestrictedDefinitionIsNotRunnableBySomebodyNotNamed() {
        signedInAs(UserRole.DEVELOPER);
        when(permissions.findByDefinitionIdAndUserId(1L, ME)).thenReturn(Optional.empty());

        assertThat(guard.mayRun(definition(DefinitionAccess.RESTRICTED))).isFalse();
    }

    @Test
    void aRestrictedDefinitionIsRunnableBySomebodyNamed() {
        signedInAs(UserRole.DEVELOPER);
        named(true, false);

        assertThat(guard.mayRun(definition(DefinitionAccess.RESTRICTED))).isTrue();
    }

    @Test
    void beingAbleToEditIsBeingAbleToRun() {
        /*
         * Somebody who may rewrite the command may obviously run the command they wrote.
         * Keeping the two apart would describe a restriction that is not there.
         */
        signedInAs(UserRole.DEVELOPER);
        named(false, true);

        assertThat(guard.mayRun(definition(DefinitionAccess.RESTRICTED))).isTrue();
    }

    @Test
    void anOpenDefinitionIsStillNotEditableWithoutAGrant() {
        /*
         * Open means open to *run*. If the role alone were enough to edit, the only way to
         * protect a definition from being rewritten would be to restrict who can run it —
         * two different questions with one answer between them.
         */
        signedInAs(UserRole.DEVELOPER);
        when(permissions.findByDefinitionIdAndUserId(1L, ME)).thenReturn(Optional.empty());

        assertThat(guard.mayEdit(definition(DefinitionAccess.OPEN))).isFalse();
    }

    @Test
    void anAdministratorIsNeverShutOut() {
        /*
         * The alternative is an administrator who restricts a definition, leaves themselves
         * off it, and can only get back in through the database. A permission system whose
         * recovery path is psql is one that gets worked around.
         */
        signedInAs(UserRole.ADMIN);
        when(permissions.findByDefinitionIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThat(guard.mayRun(definition(DefinitionAccess.RESTRICTED))).isTrue();
        assertThat(guard.mayEdit(definition(DefinitionAccess.RESTRICTED))).isTrue();
    }

    @Test
    void aRestrictedDefinitionWithNobodyOnItIsClosedRatherThanOpen() {
        /*
         * The whole reason the mode is explicit. With "empty list means everybody",
         * removing the last person would quietly reopen it to the entire installation and
         * nothing on the screen would change.
         */
        signedInAs(UserRole.DEVELOPER);
        when(permissions.findByDefinitionIdAndUserId(1L, ME)).thenReturn(Optional.empty());

        assertThat(guard.mayRun(definition(DefinitionAccess.RESTRICTED))).isFalse();
    }

    @Test
    void aListingKeepsTheOpenOnesAndTheOnesIWasNamedOn() {
        signedInAs(UserRole.DEVELOPER);

        Definition open = definition(DefinitionAccess.OPEN);
        Definition mine = definition(DefinitionAccess.RESTRICTED);
        mine.setId(2L);
        Definition theirs = definition(DefinitionAccess.RESTRICTED);
        theirs.setId(3L);

        when(permissions.findByUserId(ME)).thenReturn(List.of(
                DefinitionPermission.builder().definition(mine).canRun(true).build()));

        assertThat(guard.runnable(List.of(open, mine, theirs)))
                .containsExactly(open, mine);
    }
}
