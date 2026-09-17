package com.mcpgateway.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** An address to send a reset link to, if it turns out to have an account. */
public record ForgotPasswordRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "A valid email is required")
        String email) {
}
