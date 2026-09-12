package com.mcpgateway.dto.response;

/** Token pair returned by login, register and refresh. */
public record AuthResponse(String accessToken,
                           String refreshToken,
                           String tokenType,
                           long expiresIn,
                           UserResponse user) {

    public static AuthResponse bearer(String accessToken, String refreshToken,
                                      long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
