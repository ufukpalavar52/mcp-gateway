package com.mcpgateway.controller;

import com.mcpgateway.dto.request.LoginRequest;
import com.mcpgateway.dto.request.RefreshTokenRequest;
import com.mcpgateway.dto.request.RegisterRequest;
import com.mcpgateway.dto.response.AuthResponse;
import com.mcpgateway.security.AuthenticatedUser;
import com.mcpgateway.security.SecurityUtils;
import com.mcpgateway.service.intf.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Credential endpoints.
 *
 * <p>Login, register and refresh are the only unauthenticated routes in the API;
 * everything else requires a valid access token.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
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
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutEverywhere() {
        SecurityUtils.currentUserId().ifPresent(authService::logoutEverywhere);
        return ResponseEntity.noContent().build();
    }
}
