package com.mcpgateway.dto.request;

import com.mcpgateway.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Invitation payload for {@code POST /api/v1/users/invitations}. */
public record InviteUserRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        String email,

        @NotNull(message = "Role is required")
        UserRole role,

        Long teamId) {
}
