package com.mcpgateway.service.intf;

import com.mcpgateway.dto.request.LoginRequest;
import com.mcpgateway.dto.request.RefreshTokenRequest;
import com.mcpgateway.dto.request.RegisterRequest;
import com.mcpgateway.dto.response.AuthResponse;

/** Credential exchange and token lifecycle. */
public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);

    /** Exchanges a refresh token for a new pair, revoking the presented one. */
    AuthResponse refresh(RefreshTokenRequest request);

    /** Revokes the tokens of the current session. */
    void logout(String accessTokenId);

    /** Revokes every token of the given user, for example after a password change. */
    void logoutEverywhere(Long userId);
}
