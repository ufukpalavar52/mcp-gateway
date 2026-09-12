package com.mcpgateway.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Refresh token exchanged for a new token pair. */
public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token is required")
        String refreshToken) {
}
