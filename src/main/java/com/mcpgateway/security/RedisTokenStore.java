package com.mcpgateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Redis backed {@link TokenStore}.
 *
 * <p>Layout:
 * <ul>
 *   <li>{@code mcp:token:{type}:{tokenId}} → user id, expiring with the token</li>
 *   <li>{@code mcp:token:user:{userId}} → set of {@code {type}:{tokenId}} entries,
 *       so every token of a user can be revoked at once</li>
 * </ul>
 *
 * <p>The per-user set is pruned on revocation; stale members left behind by expiry
 * are harmless because membership alone never authorises anything.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenStore implements TokenStore {

    private static final String TOKEN_KEY_PREFIX = "mcp:token:";
    private static final String USER_KEY_PREFIX = "mcp:token:user:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void register(TokenType type, String tokenId, Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(tokenKey(type, tokenId), String.valueOf(userId), ttl);

        String userKey = userKey(userId);
        redisTemplate.opsForSet().add(userKey, member(type, tokenId));
        // The index must outlive the longest token it points at.
        redisTemplate.expire(userKey, ttl.plusDays(1));
    }

    @Override
    public boolean isActive(TokenType type, String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey(type, tokenId)));
    }

    @Override
    public void revoke(TokenType type, String tokenId) {
        String key = tokenKey(type, tokenId);
        String userId = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);

        if (userId != null) {
            redisTemplate.opsForSet().remove(userKey(Long.valueOf(userId)), member(type, tokenId));
        }
    }

    @Override
    public void revokeAllForUser(Long userId) {
        String userKey = userKey(userId);
        Set<String> members = redisTemplate.opsForSet().members(userKey);

        if (members != null) {
            members.stream()
                    .map(member -> TOKEN_KEY_PREFIX + member)
                    .forEach(redisTemplate::delete);
        }
        redisTemplate.delete(userKey);
        log.debug("Revoked every token of user {}", userId);
    }

    private String tokenKey(TokenType type, String tokenId) {
        return TOKEN_KEY_PREFIX + member(type, tokenId);
    }

    private String member(TokenType type, String tokenId) {
        return type.claimValue() + ":" + tokenId;
    }

    private String userKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }
}
