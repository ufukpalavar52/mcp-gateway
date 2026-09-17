package com.mcpgateway.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new password, set against a reset link.
 *
 * <p>No current password: not knowing it is the entire reason somebody is here. The link
 * is the authorisation, which is why it lasts an hour and works once.
 */
public record ResetPasswordRequest(

        @NotBlank(message = "A new password is required")
        @Size(min = 12, message = "Password must be at least 12 characters")
        String newPassword) {
}
