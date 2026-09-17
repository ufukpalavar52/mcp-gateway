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
                           Instant createdAt,

                           /**
                            * Whether this password was chosen by somebody other than its
                            * owner.
                            *
                            * <p>Carried here because the panel locks itself to the password
                            * screen on it, and the panel only ever sees a user through this
                            * record. Set in the database and left out of the response, the
                            * flag was true and inert: the account was created, the column
                            * said so, and nothing anywhere asked the person to change
                            * anything.
                            */
                           boolean mustChangePassword) {
}
