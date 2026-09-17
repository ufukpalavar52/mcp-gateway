package com.mcpgateway.controller;

import com.mcpgateway.dto.request.LoginRequest;
import com.mcpgateway.dto.request.RefreshTokenRequest;
import com.mcpgateway.dto.request.AcceptInvitationRequest;
import com.mcpgateway.dto.request.ChangePasswordRequest;
import com.mcpgateway.dto.request.ForgotPasswordRequest;
import com.mcpgateway.dto.request.ResetPasswordRequest;
import com.mcpgateway.dto.response.AuthResponse;
import com.mcpgateway.dto.response.SessionsResponse;
import com.mcpgateway.security.AuthenticatedUser;
import com.mcpgateway.security.SecurityUtils;
import com.mcpgateway.service.intf.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Credential endpoints.
 *
 * <p>Login, accepting an invitation and refresh are the only unauthenticated routes;
 * everything else requires a valid access token.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final com.mcpgateway.security.TokenStore tokenStore;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Sets a password against an invitation and signs the person in.
     *
     * <p>Unauthenticated, necessarily: the caller has no account yet. The token is the
     * whole of the authorisation, which is why it is 256 random bits, single use and
     * short-lived.
     */
    @PostMapping("/invitations/{token}/accept")
    public ResponseEntity<AuthResponse> accept(@PathVariable String token,
                                               @Valid @RequestBody AcceptInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.acceptInvitation(token, request));
    }

    /**
     * Changes your own password.
     *
     * <p>Reachable while the panel is locked to the password screen — it has to be, since
     * that lock is what sends people here.
     */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Asks for a reset link.
     *
     * <p>Unauthenticated, necessarily — somebody who could sign in would not be here. It
     * answers the same whether the address has an account or not: anything else turns the
     * login page into a way of finding out which addresses are worth attacking.
     */
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);

        // 204 rather than 202: nothing is queued. The mail is sent before this returns, so
        // "accepted for processing" would describe work that is already done — and every
        // other endpoint here that returns nothing says 204.
        return ResponseEntity.noContent().build();
    }

    /**
     * Sets a password against a reset link.
     *
     * <p>No current password: not knowing it is why somebody is here. The token in the
     * path is the whole of the authorisation, which is why it lasts an hour, works once,
     * and takes every session of that account with it when it is used.
     */
    @PostMapping("/password/reset/{token}")
    public ResponseEntity<Void> resetPassword(@PathVariable String token,
                                              @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(token, request);
        return ResponseEntity.noContent().build();
    }

    /** Rotates the pair: the presented refresh token is revoked as the new one is issued. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    /** Revokes the token of the current session; the client should discard both tokens. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        SecurityUtils.currentUser()
                .map(AuthenticatedUser::tokenId)
                .ifPresent(authService::logout);

        return ResponseEntity.noContent().build();
    }

    /** Revokes every token of the current user, ending all their sessions. */
    /**
     * How many sign-ins of yours are still live.
     *
     * <p>A count and nothing more: no device and no place, because neither is recorded.
     * What it is for is the question somebody actually asks here — "is anything signed in
     * that should not be" — which the button below answers.
     */
    @GetMapping("/sessions")
    public ResponseEntity<SessionsResponse> sessions() {
        return ResponseEntity.ok(new SessionsResponse(
                SecurityUtils.currentUserId().map(tokenStore::liveSessionsOf).orElse(0)));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutEverywhere() {
        SecurityUtils.currentUserId().ifPresent(authService::logoutEverywhere);
        return ResponseEntity.noContent().build();
    }
}
