package com.mcpgateway.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Reads the current principal without every service having to know about Spring Security. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public static Optional<Long> currentUserId() {
        return currentUser().map(AuthenticatedUser::id);
    }

    /** Label used in audit records when no authenticated user is present. */
    public static String currentActorLabel() {
        return currentUser().map(AuthenticatedUser::email).orElse("system");
    }
}
