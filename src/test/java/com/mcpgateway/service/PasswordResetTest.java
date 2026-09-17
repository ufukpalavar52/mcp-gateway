package com.mcpgateway.service;

import com.mcpgateway.common.exception.AuthenticationFailedException;
import com.mcpgateway.domain.entity.PasswordReset;
import com.mcpgateway.domain.entity.User;
import com.mcpgateway.domain.enums.UserRole;
import com.mcpgateway.domain.enums.UserStatus;
import com.mcpgateway.dto.request.ForgotPasswordRequest;
import com.mcpgateway.dto.request.ResetPasswordRequest;
import com.mcpgateway.mapper.UserMapper;
import com.mcpgateway.repository.PasswordResetRepository;
import com.mcpgateway.repository.UserInvitationRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.InvitationTokens;
import com.mcpgateway.security.JwtTokenService;
import com.mcpgateway.security.TokenStore;
import com.mcpgateway.service.impl.AuthServiceImpl;
import com.mcpgateway.service.intf.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
 * Forgetting a password, and getting back in.
 *
 * <p>The link is the whole of the authorisation — somebody holding one can set a password
 * without knowing the old one — so most of what follows is about refusing it.
 */
class PasswordResetTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetRepository resets = mock(PasswordResetRepository.class);
    private final InvitationMailer mailer = mock(InvitationMailer.class);
    private final TokenStore tokenStore = mock(TokenStore.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final InvitationTokens tokens = new InvitationTokens();

    private final AuthServiceImpl service = new AuthServiceImpl(
            users, encoder, mock(JwtTokenService.class), tokenStore, mock(UserMapper.class),
            mock(AuditService.class), mock(UserInvitationRepository.class), tokens,
            resets, mailer);

    private User account() {
        User user = User.builder()
                .email("kayip@example.com")
                .passwordHash(encoder.encode("the-forgotten-one"))
                .role(UserRole.DEVELOPER)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(true)
                .build();
        user.setId(7L);
        return user;
    }

    private PasswordReset link(User user, Instant expires, Instant used) {
        PasswordReset reset = PasswordReset.builder()
                .user(user)
                .tokenHash(tokens.hash("the-link"))
                .expiresAt(expires)
                .usedAt(used)
                .build();

        when(resets.findByTokenHash(tokens.hash("the-link"))).thenReturn(Optional.of(reset));
        return reset;
    }

    @Test
    void anAddressWithNoAccountIsAnsweredTheSameAndSendsNothing() {
        /*
         * The caller never learns the difference. An endpoint that says "no such account"
         * gets handed a list of addresses to find out which are worth attacking — and this
         * one is open to anybody who can reach the login page.
         */
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        service.forgotPassword(new ForgotPasswordRequest("kimse@example.com"));

        verify(mailer, never()).sendReset(anyString(), anyString(), any());
        verify(resets, never()).save(any());
    }

    @Test
    void anAddressWithAnAccountGetsALink() {
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(account()));
        when(resets.save(any())).thenAnswer(call -> call.getArgument(0));

        service.forgotPassword(new ForgotPasswordRequest("kayip@example.com"));

        verify(mailer).sendReset(anyString(), anyString(), any());
    }

    @Test
    void theLinkSetsThePasswordAndIsSpent() {
        User user = account();
        PasswordReset reset = link(user, Instant.now().plus(1, ChronoUnit.HOURS), null);

        service.resetPassword("the-link", new ResetPasswordRequest("a-brand-new-one"));

        assertThat(encoder.matches("a-brand-new-one", user.getPasswordHash())).isTrue();
        assertThat(reset.getUsedAt()).isNotNull();
    }

    @Test
    void everySessionOfThatAccountEnds() {
        /*
         * Somebody resetting because their account was taken would otherwise leave whoever
         * took it signed in — which undoes the whole reason for resetting.
         */
        User user = account();
        link(user, Instant.now().plus(1, ChronoUnit.HOURS), null);

        service.resetPassword("the-link", new ResetPasswordRequest("a-brand-new-one"));

        verify(tokenStore).revokeAllForUser(7L);
    }

    @Test
    void itAlsoClearsAnAdministratorsChosenPasswordFlag() {
        /* They have chosen their own now, which is the thing that flag was waiting for. */
        User user = account();
        link(user, Instant.now().plus(1, ChronoUnit.HOURS), null);

        service.resetPassword("the-link", new ResetPasswordRequest("a-brand-new-one"));

        assertThat(user.isMustChangePassword()).isFalse();
    }

    @Test
    void anExpiredLinkIsRefused() {
        link(account(), Instant.now().minus(1, ChronoUnit.MINUTES), null);

        assertThatThrownBy(() ->
                service.resetPassword("the-link", new ResetPasswordRequest("a-brand-new-one")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void aLinkThatWasAlreadyUsedIsRefused() {
        link(account(), Instant.now().plus(1, ChronoUnit.HOURS), Instant.now());

        assertThatThrownBy(() ->
                service.resetPassword("the-link", new ResetPasswordRequest("a-brand-new-one")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void aLinkNobodyIssuedIsRefusedInTheSameWords() {
        /* Expired, spent and invented are one answer: telling them apart is a way to learn
           which links exist. */
        when(resets.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.resetPassword("invented", new ResetPasswordRequest("a-brand-new-one")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("no longer usable");
    }
}
