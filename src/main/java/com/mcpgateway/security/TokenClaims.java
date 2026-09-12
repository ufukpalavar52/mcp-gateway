package com.mcpgateway.security;

import com.mcpgateway.domain.enums.UserRole;

/** Everything the filter needs from a verified token, without touching the database. */
public record TokenClaims(String tokenId, Long userId, String email, UserRole role, TokenType type) {
}
