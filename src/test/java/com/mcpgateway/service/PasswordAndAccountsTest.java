package com.mcpgateway.service;

import com.mcpgateway.common.exception.AuthenticationFailedException;
import com.mcpgateway.common.exception.BusinessRuleException;
import com.mcpgateway.domain.entity.User;
import com.mcpgateway.domain.enums.UserRole;
import com.mcpgateway.domain.enums.UserStatus;
import com.mcpgateway.dto.request.ChangePasswordRequest;
import com.mcpgateway.mapper.UserMapper;
import com.mcpgateway.repository.UserInvitationRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.AuthenticatedUser;
import com.mcpgateway.security.InvitationTokens;
import com.mcpgateway.security.JwtTokenService;
import com.mcpgateway.security.TokenStore;
import com.mcpgateway.service.impl.AuthServiceImpl;
import com.mcpgateway.service.intf.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Changing your own password, and the flag an administrator's chosen one sets.
 *
 * <p>Two people know a password an administrator picked, and only one of them owns the
 * account. The panel locks to the password screen until that is no longer true.
 */
class PasswordAndAccountsTest {

    private static final Long ME = 7L;

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private final AuthServiceImpl service = new AuthServiceImpl(
            users, encoder, mock(JwtTokenService.class), mock(TokenStore.class),
            mock(UserMapper.class), mock(AuditService.class),
            mock(UserInvitationRepository.class), new InvitationTokens(),
            mock(com.mcpgateway.repository.PasswordResetRepository.class),
            mock(com.mcpgateway.service.InvitationMailer.class));

    private User me;

    @BeforeEach
    void signIn() {
        me = User.builder()
                .email("me@example.com")
                .passwordHash(encoder.encode("the-old-password"))
                .role(UserRole.DEVELOPER)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(true)
                .build();
        me.setId(ME);

        when(users.findById(ME)).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(ME, "me@example.com", UserRole.DEVELOPER, "t"),
                        null, List.of()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void changingItClearsTheFlagThatLockedThePanel() {
        service.changePassword(new ChangePasswordRequest("the-old-password", "a-brand-new-one"));

        assertThat(me.isMustChangePassword()).isFalse();
        assertThat(encoder.matches("a-brand-new-one", me.getPasswordHash())).isTrue();
    }

    @Test
    void theCurrentPasswordHasToBeRight() {
        /*
         * Asked for although the caller already holds a session, and that is the point: a
         * screen left open is a session anybody walking past has, and the first useful
         * thing to do with a borrowed one is change the password and keep it.
         */
        // Not AuthenticationFailedException, and the difference is the whole point: 401
        // made the panel's client treat a mistyped field as a dead session — it refreshed,
        // retried, got the same answer and signed the person out. They were then told
        // their new password did not work, because it had never been set.
        assertThatThrownBy(() ->
                service.changePassword(new ChangePasswordRequest("guessing", "a-brand-new-one")))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(me.isMustChangePassword()).isTrue();
    }

    @Test
    void theNewPasswordHasToBeDifferent() {
        /*
         * Typing the same one back is usually answering the wrong question — and for an
         * account whose password an administrator chose, accepting it would leave in place
         * exactly the thing this flow exists to end.
         */
        assertThatThrownBy(() ->
                service.changePassword(new ChangePasswordRequest("the-old-password", "the-old-password")))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(me.isMustChangePassword()).isTrue();
    }
}
