package com.mcpgateway.service.impl;

import com.mcpgateway.common.exception.AuthenticationFailedException;
import com.mcpgateway.common.exception.ConflictException;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final TokenStore tokenStore;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final UserInvitationRepository invitationRepository;
    private final InvitationTokens invitationTokens;

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
