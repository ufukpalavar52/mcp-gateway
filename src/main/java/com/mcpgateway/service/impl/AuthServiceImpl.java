package com.mcpgateway.service.impl;

import com.mcpgateway.common.exception.AuthenticationFailedException;
import com.mcpgateway.dto.request.ChangePasswordRequest;
import com.mcpgateway.common.exception.BusinessRuleException;
import com.mcpgateway.security.SecurityUtils;
import com.mcpgateway.common.exception.ConflictException;
import com.mcpgateway.domain.entity.PasswordReset;
import com.mcpgateway.dto.request.ForgotPasswordRequest;
import com.mcpgateway.dto.request.ResetPasswordRequest;
import com.mcpgateway.domain.entity.User;
import com.mcpgateway.security.InvitationTokens;
import com.mcpgateway.repository.UserInvitationRepository;
import com.mcpgateway.domain.entity.UserInvitation;
import com.mcpgateway.domain.enums.UserRole;
import com.mcpgateway.domain.enums.UserStatus;
import com.mcpgateway.dto.request.LoginRequest;
import com.mcpgateway.dto.request.RefreshTokenRequest;
import com.mcpgateway.dto.request.AcceptInvitationRequest;
import com.mcpgateway.dto.response.AuthResponse;
import com.mcpgateway.mapper.UserMapper;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.JwtTokenService;
import com.mcpgateway.security.TokenClaims;
import com.mcpgateway.security.TokenStore;
import com.mcpgateway.security.TokenType;
import com.mcpgateway.service.intf.AuditService;
import com.mcpgateway.service.intf.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Password and token flows.
 *
 * <p>Refresh uses rotation: the presented refresh token is revoked as soon as a new
 * pair is issued, so a stolen refresh token is usable at most once, and the theft
 * becomes visible when the legitimate client's next refresh is rejected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Returned for both unknown accounts and wrong passwords, so neither is discoverable. */
    private static final String INVALID_CREDENTIALS = "Email or password is incorrect";

    /** How long a reset link lives. A password in a mailbox, so: not long. */
    private static final java.time.Duration RESET_WINDOW = java.time.Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final TokenStore tokenStore;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final UserInvitationRepository invitationRepository;
    private final InvitationTokens invitationTokens;
    private final com.mcpgateway.repository.PasswordResetRepository resetRepository;
    private final com.mcpgateway.service.InvitationMailer mailer;

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_CREDENTIALS));

        if (!user.canAuthenticate()
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }

        user.setLastLoginAt(Instant.now());
        auditService.recordAs(user.getId(), user.getEmail(), "auth.login", "user", user.getId(), Map.of());

        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthResponse acceptInvitation(String token, AcceptInvitationRequest request) {
        // Found by hash, because only the hash was kept. A token that matches nothing and a
        // token that expired are told apart in the log and not on the screen: to somebody
        // holding a link, "this link is no longer usable" is the whole of what is safe to
        // say, and the difference between the two is a way to learn which links exist.
        UserInvitation invitation = invitationRepository
                .findByTokenHash(invitationTokens.hash(token))
                .filter(found -> found.isRedeemable(Instant.now()))
                .orElseThrow(() -> new AuthenticationFailedException("This invitation is no longer usable"));

        if (userRepository.existsByEmailIgnoreCase(invitation.getEmail())) {
            // Somebody was invited to an account they already have. The invitation is spent
            // rather than left lying about, and they are sent to sign in with the password
            // they already chose — creating a second account, or quietly overwriting the
            // password on the first, would both be worse than saying so.
            invitation.setAcceptedAt(Instant.now());
            throw new ConflictException("An account with this email already exists; sign in instead");
        }

        // Email and role off the invitation, never out of the request. Reading them from
        // the body would let whoever holds one link make an account for any address, in any
        // role — which is the whole of the authorisation this endpoint has.
        User user = User.builder()
                .email(invitation.getEmail())
                .fullName(request.fullName())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(invitation.getRole())
                .status(UserStatus.ACTIVE)
                .build();

        User saved = userRepository.save(user);
        invitation.setAcceptedAt(Instant.now());

        auditService.recordAs(saved.getId(), saved.getEmail(), "auth.invitation.accepted",
                "user", saved.getId(), Map.of("invitation", invitation.getId()));
        log.info("Invitation {} accepted as account {} ({})",
                invitation.getId(), saved.getId(), saved.getRole());

        return issueTokens(saved);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        TokenClaims claims = tokenService.parse(request.refreshToken())
                .filter(parsed -> parsed.type() == TokenType.REFRESH)
                .orElseThrow(() -> new AuthenticationFailedException("Refresh token is invalid"));

        if (!tokenStore.isActive(TokenType.REFRESH, claims.tokenId())) {
            // Either already rotated, revoked by logout, or replayed by an attacker.
            throw new AuthenticationFailedException("Refresh token is no longer valid");
        }

        User user = userRepository.findById(claims.userId())
                .filter(User::canAuthenticate)
                .orElseThrow(() -> new AuthenticationFailedException("Account is no longer active"));

        tokenStore.revoke(TokenType.REFRESH, claims.tokenId());
        return issueTokens(user);
    }

    @Override
    public void logout(String accessTokenId) {
        tokenStore.revoke(TokenType.ACCESS, accessTokenId);
    }

    @Override
    public void logoutEverywhere(Long userId) {
        tokenStore.revokeAllForUser(userId);
    }

    /** Issues a pair and registers both ids so they can be revoked before they expire. */
    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = SecurityUtils.currentUserId()
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new AuthenticationFailedException("Not signed in"));

        // Asked for even though the caller already holds a session, and that is the point:
        // a screen left open is a session anybody walking past has, and the first useful
        // thing to do with a borrowed one is change the password and keep it. The old
        // password authenticates the person rather than the session.
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            // 422 rather than 401, and the distinction is not pedantry: the caller *is*
            // authenticated — what is wrong is a field they typed. Answering 401 made the
            // panel's client read it as an expired session, refresh the token, retry, get
            // the same answer and sign the person out. Mistyping a password logged you
            // out of a session that was perfectly valid.
            //
            // "Who are you" and "what did you type" are different questions and need
            // different answers.
            throw new BusinessRuleException("The current password is not right");
        }

        // Refused rather than quietly accepted: somebody typing the same password back is
        // usually answering the wrong question, and for an account whose password an
        // administrator chose it would leave the thing this flow exists to end.
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("The new password must be different");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);

        auditService.recordAs(user.getId(), user.getEmail(), "auth.password.changed",
                "user", user.getId(), Map.of());
        log.info("Password changed for account {}", user.getId());
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // The same answer either way, and it is the caller who never learns the
        // difference: an endpoint that says "no such account" will be handed a list of
        // addresses to find out which ones are worth attacking, and this one is open to
        // anybody who can reach the login page.
        //
        // So the work is done when there is somebody to do it for, and the method returns
        // quietly when there is not. The log keeps the distinction, because the operator is
        // allowed to know it.
        userRepository.findByEmailIgnoreCase(request.email()).ifPresentOrElse(user -> {
            String token = invitationTokens.mint();

            PasswordReset reset = PasswordReset.builder()
                    .user(user)
                    .tokenHash(invitationTokens.hash(token))
                    // An hour, not an invitation's seven days. An invitation moves at the
                    // speed of hiring somebody; a reset link is a password, and the less
                    // time it spends sitting in a mailbox the better.
                    .expiresAt(Instant.now().plus(RESET_WINDOW))
                    .build();

            PasswordReset saved = resetRepository.save(reset);

            // Inside the transaction: a link that could not be sent should not leave a row
            // behind that still opens the account.
            mailer.sendReset(user.getEmail(), token, saved.getExpiresAt());

            auditService.recordAs(user.getId(), user.getEmail(), "auth.password.reset.requested",
                    "user", user.getId(), Map.of());
            log.info("Password reset requested for account {}", user.getId());
        }, () -> log.info("Password reset asked for {}, which has no account", request.email()));
    }

    @Override
    @Transactional
    public void resetPassword(String token, ResetPasswordRequest request) {
        PasswordReset reset = resetRepository.findByTokenHash(invitationTokens.hash(token))
                .filter(found -> found.isRedeemable(Instant.now()))
                // Expired, used and never-issued are one answer. Telling them apart is a
                // way to learn which links exist.
                .orElseThrow(() -> new AuthenticationFailedException("This link is no longer usable"));

        User user = reset.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));

        // Whatever an administrator set is beside the point now: they have chosen their own.
        user.setMustChangePassword(false);
        reset.setUsedAt(Instant.now());

        // Every session of theirs ends. Somebody resetting because their account was taken
        // would otherwise leave whoever took it signed in — which undoes the whole reason
        // for resetting.
        tokenStore.revokeAllForUser(user.getId());

        auditService.recordAs(user.getId(), user.getEmail(), "auth.password.reset",
                "user", user.getId(), Map.of());
        log.info("Password reset for account {}; every session revoked", user.getId());
    }

    private AuthResponse issueTokens(User user) {
        JwtTokenService.IssuedToken access = tokenService.issue(user, TokenType.ACCESS);
        JwtTokenService.IssuedToken refresh = tokenService.issue(user, TokenType.REFRESH);

        tokenStore.register(TokenType.ACCESS, access.tokenId(), user.getId(), access.ttl());
        tokenStore.register(TokenType.REFRESH, refresh.tokenId(), user.getId(), refresh.ttl());

        return AuthResponse.bearer(
                access.token(),
                refresh.token(),
                access.ttl().toSeconds(),
                userMapper.toResponse(user));
    }
}
