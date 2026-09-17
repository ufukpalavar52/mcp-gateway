package com.mcpgateway.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Turning an invitation into an account.
 *
 * <p>No email and no role: both are decided by whoever sent the invitation and read off it.
 * Accepting one is the act of setting a password, not of describing yourself — a form that
 * took an email would let the invited person create an account for somebody else.
 */
public record AcceptInvitationRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 160, message = "Name must be at most 160 characters")
        String fullName,

        @NotBlank(message = "Password is required")
        @Size(min = 12, message = "Password must be at least 12 characters")
        String password) {
}
