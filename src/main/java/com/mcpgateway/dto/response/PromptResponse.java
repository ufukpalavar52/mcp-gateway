package com.mcpgateway.dto.response;

import com.mcpgateway.client.McpServerClient;

/**
 * What a prompt turned into, and where it was written down.
 *
 * <p>The routing result is passed through untouched; {@code conversationRef} is what the
 * panel needs to keep asking into the same conversation, and it is returned rather than
 * chosen by the caller so that a browser cannot append to a session it does not own.
 */
public record PromptResponse(String conversationRef,
                             Long turnId,
                             McpServerClient.PromptResult result) {
}
