package com.mcpgateway.dto.request;

import com.mcpgateway.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An account an administrator creates outright, password and all.
 *
 * <p>The other way in is an invitation, where the person chooses their own password and
 * nobody else ever knows it. This exists for when that is not practical — no mail for the
 * address, somebody standing next to you, a server that cannot reach an SMTP host — and
 * because it is the one way to add a user that needs nothing but the database. That makes
 * it the way back in when mail is misconfigured, which is why both exist.
 *
 * <p>The password it carries is marked as needing to change: two people know it, and only
 * one of them owns the account.
 */
public record CreateUserRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 160, message = "Name must be at most 160 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "A valid email is required")
        String email,

        @NotNull(message = "Role is required")
        UserRole role,

        @NotBlank(message = "Password is required")
        @Size(min = 12, message = "Password must be at least 12 characters")
        String password) {
}
