package com.mcpgateway.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Invitation tokens: minting one, and recognising it again.
 *
 * <p>A component rather than two private methods, because two services need them and they
 * have to agree exactly — the token is shown once when the invitation is created and then
 * only ever presented back, so a difference of one character in how it is hashed would make
 * every invitation permanently unacceptable with nothing to say why.
 *
 * <p>Only the hash is stored. An invitation row is enough to add somebody to the system, so
 * a leaked database should not also hand over the links.
 */
@Component
public class InvitationTokens {

    private final SecureRandom random = new SecureRandom();

    /** A fresh token. Returned to the inviter once and never stored as it stands. */
    public String mint() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * What gets stored, and what a presented token is looked up by.
     *
     * <p>Plain SHA-256 rather than a password hash, and deliberately: this is a 256-bit
     * random value, not something a person chose, so there is nothing to guess at and
     * nothing for a slow hash to defend.
     */
    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(
                    digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
