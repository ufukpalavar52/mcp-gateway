package com.mcpgateway.service;

import com.mcpgateway.common.exception.AuthenticationFailedException;
import com.mcpgateway.common.exception.ConflictException;
import com.mcpgateway.domain.entity.User;
import com.mcpgateway.domain.entity.UserInvitation;
import com.mcpgateway.domain.enums.UserRole;
import com.mcpgateway.dto.request.AcceptInvitationRequest;
import com.mcpgateway.mapper.UserMapper;
import com.mcpgateway.repository.UserInvitationRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.InvitationTokens;
import com.mcpgateway.security.JwtTokenService;
import com.mcpgateway.security.TokenStore;
import com.mcpgateway.service.impl.AuthServiceImpl;
import com.mcpgateway.service.intf.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The only way into this system.
 *
 * <p>Open registration was removed. An installation reaches a database and a fleet of
 * servers, and the address of its login page is not a secret — "anybody who can find it can
 * have an account" was never a defensible default, however small the deployment.
 *
 * <p>What replaces it is an invitation somebody with an account had to create. The token is
 * the whole of the authorisation, which is why everything below is about refusing it.
 */
class AcceptInvitationTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserInvitationRepository invitations = mock(UserInvitationRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final InvitationTokens invitationTokens = new InvitationTokens();

    private final AuthServiceImpl service = new AuthServiceImpl(
            users, encoder, tokens, mock(TokenStore.class), mock(UserMapper.class),
            mock(AuditService.class), invitations, invitationTokens,
            mock(com.mcpgateway.repository.PasswordResetRepository.class),
            mock(com.mcpgateway.service.InvitationMailer.class));

    private static final AcceptInvitationRequest REQUEST =
            new AcceptInvitationRequest("Yeni Kisi", "a-long-enough-password");

    @BeforeEach
    void stubs() {
        when(encoder.encode(anyString())).thenReturn("hashed");
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        when(tokens.issue(any(), any())).thenReturn(
                new JwtTokenService.IssuedToken("t", "id", java.time.Duration.ofMinutes(15)));
    }

    private UserInvitation invitation(UserRole role, Instant expires, Instant accepted) {
        UserInvitation invitation = UserInvitation.builder()
                .email("davetli@example.com")
                .role(role)
                .tokenHash(invitationTokens.hash("the-token"))
                .expiresAt(expires)
                .build();
        invitation.setId(1L);
        invitation.setAcceptedAt(accepted);

        when(invitations.findByTokenHash(invitationTokens.hash("the-token")))
                .thenReturn(Optional.of(invitation));
        return invitation;
    }

    private User saved() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void theAccountTakesTheEmailAndRoleFromTheInvitation() {
        /*
         * Never from the request. Reading them from the body would let whoever holds one
         * link make an account for any address, in any role — which is the whole of the
         * authorisation this endpoint has.
         */
        invitation(UserRole.DEVELOPER, Instant.now().plus(1, ChronoUnit.DAYS), null);

        service.acceptInvitation("the-token", REQUEST);

        assertThat(saved().getEmail()).isEqualTo("davetli@example.com");
        assertThat(saved().getRole()).isEqualTo(UserRole.DEVELOPER);
        assertThat(saved().getFullName()).isEqualTo("Yeni Kisi");
    }

    @Test
    void theTokenIsSpentOnceItHasBeenUsed() {
        UserInvitation invitation =
                invitation(UserRole.VIEWER, Instant.now().plus(1, ChronoUnit.DAYS), null);

        service.acceptInvitation("the-token", REQUEST);

        assertThat(invitation.getAcceptedAt()).isNotNull();
    }

    @Test
    void anAlreadyAcceptedInvitationIsRefused() {
        invitation(UserRole.VIEWER, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now());

        assertThatThrownBy(() -> service.acceptInvitation("the-token", REQUEST))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(users, never()).save(any());
    }

    @Test
    void anExpiredInvitationIsRefused() {
        invitation(UserRole.VIEWER, Instant.now().minus(1, ChronoUnit.DAYS), null);

        assertThatThrownBy(() -> service.acceptInvitation("the-token", REQUEST))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(users, never()).save(any());
    }

    @Test
    void aTokenNobodyIssuedIsRefusedTheSameWay() {
        /*
         * The same exception and the same words as an expired one. To somebody holding a
         * link, "this link is no longer usable" is the whole of what is safe to say —
         * telling the two apart is a way to learn which links exist.
         */
        when(invitations.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptInvitation("invented", REQUEST))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void anInvitationToAnAddressThatAlreadyHasAnAccountMakesNoSecondOne() {
        /*
         * And the invitation is spent rather than left lying about. Creating a second
         * account, or quietly resetting the password on the first, would both be worse
         * than saying so.
         */
        UserInvitation invitation =
                invitation(UserRole.VIEWER, Instant.now().plus(1, ChronoUnit.DAYS), null);
        when(users.existsByEmailIgnoreCase("davetli@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.acceptInvitation("the-token", REQUEST))
                .isInstanceOf(ConflictException.class);

        verify(users, never()).save(any());
        assertThat(invitation.getAcceptedAt()).isNotNull();
    }
}
