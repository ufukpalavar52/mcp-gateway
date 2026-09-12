package com.mcpgateway.dto.request;

import com.mcpgateway.domain.enums.UserRole;
import com.mcpgateway.domain.enums.UserStatus;
import jakarta.validation.constraints.Size;

/** Administrative update of another account. Null members are left untouched. */
public record UpdateUserRequest(

        @Size(min = 3, max = 120, message = "Full name must be between 3 and 120 characters")
        String fullName,

        UserRole role,

        UserStatus status,

        Long teamId) {
}
