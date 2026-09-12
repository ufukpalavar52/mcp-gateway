package com.mcpgateway.security;

/**
 * A freshly issued access and refresh token together with the access token lifetime
 * in seconds, which clients use to schedule their refresh.
 */
public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
}
