package com.mcpgateway.mapper;

import com.mcpgateway.domain.entity.User;
import com.mcpgateway.domain.entity.UserInvitation;
import com.mcpgateway.dto.response.InvitationResponse;
import com.mcpgateway.dto.response.UserResponse;
import org.springframework.stereotype.Component;

/** Entity to response translation for accounts and invitations. */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus(),
                user.getTeam() == null ? null : user.getTeam().getName(),
                user.getAvatarUrl(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.isMustChangePassword());
    }

    /**
     * Invitation view.
     *
     * <p>The raw token is passed in rather than read from the entity: only its hash is
     * stored, so this is the one and only moment it can be returned.
     */
    public InvitationResponse toResponse(UserInvitation invitation, String rawToken) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getRole(),
                rawToken,
                invitation.getExpiresAt());
    }
}
