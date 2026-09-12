package com.mcpgateway.security;

import com.mcpgateway.property.JwtProperties;
import com.mcpgateway.domain.entity.User;
import com.mcpgateway.domain.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies JWTs.
 *
 * <p>Responsibility stops at the cryptography: whether a structurally valid token is
 * still <em>accepted</em> is decided by {@link TokenStore}. Keeping the two apart lets
 * the revocation strategy change without touching token construction.
 */
@Slf4j
@Service
public class JwtTokenService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** Builds a token and returns it together with the id that must be registered. */
    public IssuedToken issue(User user, TokenType type) {
        String tokenId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Duration ttl = ttlFor(type);

        String token = Jwts.builder()
                .id(tokenId)
                .issuer(properties.getIssuer())
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_TYPE, type.claimValue())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();

        return new IssuedToken(token, tokenId, ttl);
    }

    /**
     * Verifies signature and expiry.
     *
     * @return the claims, or empty when the token is malformed, tampered with or expired
     */
    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new TokenClaims(
                    claims.getId(),
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    UserRole.valueOf(claims.get(CLAIM_ROLE, String.class)),
                    TokenType.valueOf(claims.get(CLAIM_TYPE, String.class).toUpperCase())));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Duration ttlFor(TokenType type) {
        return type == TokenType.ACCESS
                ? properties.getAccessTokenTtl()
                : properties.getRefreshTokenTtl();
    }

    /** A signed token plus the metadata the caller needs to register it. */
    public record IssuedToken(String token, String tokenId, Duration ttl) {
    }
}
