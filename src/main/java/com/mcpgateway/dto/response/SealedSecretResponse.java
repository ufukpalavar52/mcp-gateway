package com.mcpgateway.dto.response;

/**
 * A credential this service holds but cannot read.
 *
 * <p>Ciphertext and the id of the key that sealed it, exactly as stored. It travels to the
 * planner and on to the executor, which opens it through mcp-cipher at the moment it is
 * needed — so the plaintext exists only inside the process that is about to use it, and
 * only for as long as that takes.
 *
 * @param context what the value was sealed under; it will not open under any other
 */
public record SealedSecretResponse(byte[] ciphertext, String keyId, String context) {
}
