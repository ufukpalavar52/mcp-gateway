package com.mcpgateway.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Changing your own password.
 *
 * <p>The current one is asked for even though this endpoint is already behind a session,
 * and that is the point of it: a screen left open in a shared office is a session anybody
 * walking past has, and the first thing worth doing with a borrowed session is to change
 * the password and keep it. Knowing the old one authenticates the person rather than the
 * session.
 */
public record ChangePasswordRequest(

        @NotBlank(message = "The current password is required")
        String currentPassword,

        @NotBlank(message = "A new password is required")
        @Size(min = 12, message = "Password must be at least 12 characters")
        String newPassword) {
}
