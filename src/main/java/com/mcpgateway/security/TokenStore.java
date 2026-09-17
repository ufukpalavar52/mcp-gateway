package com.mcpgateway.security;

import java.time.Duration;

/**
 * Server side registry of issued tokens.
 *
 * <p>A JWT alone cannot be revoked before it expires, so every issued token id is
 * also recorded here. The filter accepts a token only if it is both cryptographically
 * valid and still registered, which makes logout and refresh rotation immediate.
 *
 * <p>Declared as an interface so the storage engine stays replaceable; the Redis
 * implementation is only one possible binding.
 */
public interface TokenStore {

    /** Registers a token id for the given user with the token's remaining lifetime. */
    void register(TokenType type, String tokenId, Long userId, Duration ttl);

    /** True when the token id is still registered and therefore usable. */
    boolean isActive(TokenType type, String tokenId);

    /** Revokes a single token id, for example the refresh token being rotated. */
    void revoke(TokenType type, String tokenId);

    /** Revokes every token of a user; used by logout and by account suspension. */
    void revokeAllForUser(Long userId);

    /**
     * How many sign-ins of this person are still live.
     *
     * <p>Refresh tokens are counted and access tokens are not: one sign-in issues one of
     * each, and the access token is replaced every few minutes. Counting both would report
     * a number that climbs while somebody sits reading a page.
     *
     * <p>The index is a set with a longer expiry than the tokens it points at, so an entry
     * can outlive what it names. Members whose token is gone are not counted.
     */
    int liveSessionsOf(Long userId);
}
