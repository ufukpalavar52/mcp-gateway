package com.mcpgateway.service.intf;

import com.mcpgateway.dto.request.LoginRequest;
import com.mcpgateway.dto.request.RefreshTokenRequest;
import com.mcpgateway.dto.request.AcceptInvitationRequest;
import com.mcpgateway.dto.response.AuthResponse;

/** Credential exchange and token lifecycle. */
public interface AuthService {

    AuthResponse login(LoginRequest request);

    /**
     * Turns an invitation into an account, and signs the person in.
     *
     * <p>The only way into this system. Open registration was removed: an installation
     * reaches a database and a fleet of servers, and the address of its login page is not
     * a secret, so "anybody who can find it can have an account" was never a defensible
     * default however small the deployment.
     *
     * <p>The email and the role come off the invitation rather than out of the request —
     * they were decided by whoever sent it.
     */
    AuthResponse acceptInvitation(String token, AcceptInvitationRequest request);

    /** Exchanges a refresh token for a new pair, revoking the presented one. */
    AuthResponse refresh(RefreshTokenRequest request);

    /** Revokes the tokens of the current session. */
    void logout(String accessTokenId);

    /** Revokes every token of the given user, for example after a password change. */
    void logoutEverywhere(Long userId);
}
