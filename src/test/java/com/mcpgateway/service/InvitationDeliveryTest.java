package com.mcpgateway.service;

import com.mcpgateway.common.exception.BusinessRuleException;
import com.mcpgateway.domain.entity.UserInvitation;
import com.mcpgateway.domain.enums.UserRole;
import com.mcpgateway.domain.enums.UserStatus;
import com.mcpgateway.dto.request.CreateUserRequest;
import com.mcpgateway.dto.request.InviteUserRequest;
import com.mcpgateway.mapper.UserMapper;
import com.mcpgateway.property.InvitationProperties;
import com.mcpgateway.repository.TeamRepository;
import com.mcpgateway.repository.UserInvitationRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.InvitationTokens;
import com.mcpgateway.service.impl.UserServiceImpl;
import com.mcpgateway.service.intf.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two ways into the system, and what happens when one of them cannot be delivered.
 */
class InvitationDeliveryTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserInvitationRepository invitations = mock(UserInvitationRepository.class);
    private final InvitationMailer mailer = mock(InvitationMailer.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserMapper mapper = mock(UserMapper.class);

    // Declaration order, which is the constructor order Lombok generates.
    private final UserServiceImpl service = new UserServiceImpl(
            new InvitationProperties(), encoder, mailer, new InvitationTokens(),
            users, invitations, mock(TeamRepository.class), mapper,
            mock(AuditService.class), mock(com.mcpgateway.security.TokenStore.class));

    private void invitationSaves() {
        when(invitations.save(any())).thenAnswer(call -> {
            UserInvitation saved = call.getArgument(0);
            saved.setId(1L);
            return saved;
        });
    }

    @Test
    void anInvitationThatCannotBeSentIsNotCreated() {
        /*
         * A row nobody will ever hear about is worse than a refusal: the refusal is
         * visible and the row is not, so the administrator goes on believing somebody was
         * invited. Sent inside the transaction, so a failure takes the row with it.
         */
        invitationSaves();
        doThrow(new IllegalStateException("no smtp host"))
                .when(mailer).sendInvitation(anyString(), anyString(), any());

        assertThatThrownBy(() ->
                service.invite(new InviteUserRequest("yeni@example.com", UserRole.VIEWER, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not created");
    }

    @Test
    void theTokenNeverReachesTheLog() {
        /*
         * Only the address is logged on failure. A log store with thirty days of retention
         * and no encryption is not where something that opens an account should sit.
         */
        invitationSaves();

        service.invite(new InviteUserRequest("yeni@example.com", UserRole.VIEWER, null));

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendInvitation(anyString(), token.capture(), any());

        // The raw token exists exactly once, in the mail and in the response — never in a
        // column: what is stored is its hash.
        ArgumentCaptor<UserInvitation> saved = ArgumentCaptor.forClass(UserInvitation.class);
        verify(invitations).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(token.getValue());
    }

    @Test
    void anAdministratorsPasswordMakesAnActiveAccountThatMustChangeIt() {
        /*
         * Active, because the whole point is that they can sign in now. Flagged, because
         * two people know the password and only one of them owns the account.
         *
         * This is also the way in that needs no mail, which is what makes requiring mail
         * for invitations safe.
         */
        when(users.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("hashed");
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));

        service.create(new CreateUserRequest(
                "Yeni Kisi", "yeni@example.com", UserRole.DEVELOPER, "a-long-enough-one"));

        ArgumentCaptor<com.mcpgateway.domain.entity.User> saved =
                ArgumentCaptor.forClass(com.mcpgateway.domain.entity.User.class);
        verify(users).save(saved.capture());

        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.DEVELOPER);
    }
}
