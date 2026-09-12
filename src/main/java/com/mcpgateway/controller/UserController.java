package com.mcpgateway.controller;

import com.mcpgateway.common.dto.PageResponse;
import com.mcpgateway.dto.request.InviteUserRequest;
import com.mcpgateway.dto.request.UpdateUserRequest;
import com.mcpgateway.dto.response.InvitationResponse;
import com.mcpgateway.dto.response.UserResponse;
import com.mcpgateway.service.intf.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Account administration. Everything except {@code /me} is admin only. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** The caller's own account; every authenticated role may read it. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> findCurrent() {
        return ResponseEntity.ok(userService.findCurrent());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<UserResponse>> findAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(userService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    /** Suspends the account and immediately revokes every token it holds. */
    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> suspend(@PathVariable Long id) {
        return ResponseEntity.ok(userService.suspend(id));
    }

    /** The returned token is shown once and stored only as a hash. */
    @PostMapping("/invitations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvitationResponse> invite(@Valid @RequestBody InviteUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.invite(request));
    }
}
