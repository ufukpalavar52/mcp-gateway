package com.mcpgateway.dto.response;

import com.mcpgateway.domain.enums.UserRole;
import com.mcpgateway.domain.enums.UserStatus;

import java.time.Instant;

/** Public view of an account. The password hash never leaves the service layer. */
public record UserResponse(Long id,
                           String email,
                           String fullName,
                           UserRole role,
                           UserStatus status,
                           String team,
                           String avatarUrl,
                           Instant lastLoginAt,
                           Instant createdAt) {
}
