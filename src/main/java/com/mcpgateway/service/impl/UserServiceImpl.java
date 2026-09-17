package com.mcpgateway.service.impl;

import com.mcpgateway.common.dto.PageResponse;
import com.mcpgateway.common.exception.BusinessRuleException;
import com.mcpgateway.common.exception.ConflictException;
import com.mcpgateway.common.exception.ResourceNotFoundException;
import com.mcpgateway.domain.entity.Team;
import com.mcpgateway.domain.entity.User;
import com.mcpgateway.domain.entity.UserInvitation;
import com.mcpgateway.domain.enums.UserStatus;
import com.mcpgateway.dto.request.InviteUserRequest;
import com.mcpgateway.dto.request.UpdateUserRequest;
import com.mcpgateway.dto.response.InvitationResponse;
import com.mcpgateway.dto.response.UserResponse;
import com.mcpgateway.mapper.UserMapper;
import com.mcpgateway.property.InvitationProperties;
import com.mcpgateway.repository.TeamRepository;
import com.mcpgateway.repository.UserInvitationRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.SecurityUtils;
import com.mcpgateway.security.TokenStore;
import com.mcpgateway.service.intf.AuditService;
import com.mcpgateway.service.intf.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/** Account administration, including the invitation flow. */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String RESOURCE = "User";

    private final InvitationProperties invitationProperties;
    private final com.mcpgateway.security.InvitationTokens invitationTokens;
    private final UserRepository userRepository;
    private final UserInvitationRepository invitationRepository;
    private final TeamRepository teamRepository;
    private final UserMapper mapper;
    private final AuditService auditService;
    private final TokenStore tokenStore;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> findAll(Pageable pageable) {
        return PageResponse.from(userRepository.findAll(pageable), mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return mapper.toResponse(requireUser(id));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findCurrent() {
        Long userId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new ResourceNotFoundException("No authenticated user"));

        return mapper.toResponse(requireUser(userId));
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = requireUser(id);

        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.teamId() != null) {
            user.setTeam(requireTeam(request.teamId()));
        }
        if (request.status() != null) {
            applyStatusChange(user, request.status());
        }

        auditService.record("user.updated", "user", id, Map.of());
        return mapper.toResponse(user);
    }

    @Override
    @Transactional
    public InvitationResponse invite(InviteUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An account with this email already exists");
        }

        // The raw token is returned once; only its hash is persisted.
        String token = invitationTokens.mint();

        UserInvitation invitation = UserInvitation.builder()
                .email(request.email())
                .role(request.role())
                .team(request.teamId() == null ? null : requireTeam(request.teamId()))
                .tokenHash(invitationTokens.hash(token))
                .invitedBy(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .expiresAt(Instant.now().plus(invitationProperties.getTtl()))
                .build();

        UserInvitation saved = invitationRepository.save(invitation);
        auditService.record("user.invited", "user_invitation", saved.getId(),
                Map.of("email", request.email()));

        return mapper.toResponse(saved, token);
    }

    @Override
    @Transactional
    public UserResponse suspend(Long id) {
        User user = requireUser(id);
        applyStatusChange(user, UserStatus.SUSPENDED);

        auditService.record("user.suspended", "user", id, Map.of());
        return mapper.toResponse(user);
    }

    /* ------------------------------- helpers ------------------------------- */

    /**
     * Changes the status and, when the account loses access, revokes its tokens.
     *
     * <p>Without this a suspended user would stay authenticated until their access
     * token expired on its own.
     */
    private void applyStatusChange(User user, UserStatus status) {
        if (user.getStatus() == status) {
            return;
        }
        if (status == UserStatus.ACTIVE && user.getPasswordHash() == null) {
            throw new BusinessRuleException("Account has no password set and cannot be activated");
        }

        user.setStatus(status);

        if (status != UserStatus.ACTIVE) {
            tokenStore.revokeAllForUser(user.getId());
        }
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    private Team requireTeam(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team", id));
    }


}
