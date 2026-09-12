package com.mcpgateway.security;

import com.mcpgateway.domain.enums.UserRole;

/**
 * Principal placed on the security context. Deliberately minimal: the filter must not
 * hit the database on every request, so only what the token carries is exposed here.
 */
public record AuthenticatedUser(Long id, String email, UserRole role, String tokenId) {

    /** Spring Security authority name, for example {@code ROLE_ADMIN}. */
    public String authority() {
        return "ROLE_" + role.name();
    }
}
