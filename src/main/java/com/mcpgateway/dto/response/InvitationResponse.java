package com.mcpgateway.dto.response;

import com.mcpgateway.domain.enums.UserRole;

import java.time.Instant;

/**
 * A created invitation.
 *
 * <p>{@code token} is present only in the response that creates the invitation; it is
 * stored as a hash and can never be read back.
 */
public record InvitationResponse(Long id,
                                 String email,
                                 UserRole role,
                                 String token,
                                 Instant expiresAt) {
}
