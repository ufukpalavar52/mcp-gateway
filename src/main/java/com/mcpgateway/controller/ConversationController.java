package com.mcpgateway.controller;

import com.mcpgateway.common.dto.PageResponse;
import com.mcpgateway.dto.response.ConversationResponse;
import com.mcpgateway.service.intf.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Console sessions, each belonging to the person who had it.
 *
 * <p>Nothing here takes an owner: every method reads the caller's identity from the security
 * context. An endpoint that accepted a user id would be one missing check away from serving
 * somebody else's questions.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<ConversationResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,

            /** Words to look for in the title and in the questions asked. */
            @RequestParam(defaultValue = "") String search) {

        return ResponseEntity.ok(conversationService.findMine(
                search,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))));
    }

    @GetMapping("/{conversationRef}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationResponse> open(@PathVariable String conversationRef) {
        return ResponseEntity.ok(conversationService.open(conversationRef));
    }

    @DeleteMapping("/{conversationRef}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable String conversationRef) {
        conversationService.delete(conversationRef);
        return ResponseEntity.noContent().build();
    }
}
