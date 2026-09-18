package com.mcpgateway.service.intf;

import com.mcpgateway.common.dto.PageResponse;
import com.mcpgateway.dto.request.CreateUserRequest;
import com.mcpgateway.dto.request.InviteUserRequest;
import com.mcpgateway.dto.request.UpdateUserRequest;
import com.mcpgateway.dto.response.InvitationResponse;
import com.mcpgateway.dto.response.UserResponse;
import org.springframework.data.domain.Pageable;

/** Account administration. */
public interface UserService {

    PageResponse<UserResponse> findAll(String search, Pageable pageable);

    UserResponse findById(Long id);

    UserResponse findCurrent();

    UserResponse update(Long id, UpdateUserRequest request);

    /** Creates an invitation and returns the single-use token. */
    InvitationResponse invite(InviteUserRequest request);

    /** Suspends an account and revokes every token it holds. */
    UserResponse suspend(Long id);

    /**
     * Creates an account with a password the administrator chose.
     *
     * <p>Active straight away — they have to be able to sign in — and flagged so the panel
     * locks to the password screen until they have replaced it.
     */
    UserResponse create(CreateUserRequest request);
}
