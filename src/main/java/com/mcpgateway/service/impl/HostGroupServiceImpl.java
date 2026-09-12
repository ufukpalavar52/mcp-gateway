package com.mcpgateway.service.impl;

import com.mcpgateway.common.exception.ConflictException;
import com.mcpgateway.common.exception.ResourceNotFoundException;
import com.mcpgateway.domain.entity.HostGroup;
import com.mcpgateway.dto.request.HostGroupRequest;
import com.mcpgateway.dto.response.HostGroupResponse;
import com.mcpgateway.mapper.HostGroupMapper;
import com.mcpgateway.repository.ActionRepository;
import com.mcpgateway.repository.HostGroupRepository;
import com.mcpgateway.service.intf.AuditService;
import com.mcpgateway.service.intf.HostGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** CRUD for host groups. Host lists are normalised on the way in. */
@Service
@RequiredArgsConstructor
public class HostGroupServiceImpl implements HostGroupService {

    private static final String RESOURCE = "Host group";

    private final HostGroupRepository hostGroupRepository;
    private final ActionRepository actionRepository;
    private final HostGroupMapper mapper;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<HostGroupResponse> findAll() {
        return hostGroupRepository.findAll().stream()
                .map(group -> mapper.toResponse(group, actionRepository.countByHostGroupId(group.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HostGroupResponse findById(Long id) {
        HostGroup group = requireGroup(id);
        return mapper.toResponse(group, actionRepository.countByHostGroupId(id));
    }

    @Override
    @Transactional
    public HostGroupResponse create(HostGroupRequest request) {
        if (hostGroupRepository.existsByName(request.name())) {
            throw new ConflictException("A group named '%s' already exists".formatted(request.name()));
        }

        HostGroup group = HostGroup.builder()
                .name(request.name())
                .description(request.description() == null ? "" : request.description())
                .hosts(normalise(request.hosts()))
                .build();

        HostGroup saved = hostGroupRepository.save(group);
        auditService.record("host_group.created", "host_group", saved.getId(),
                Map.of("hostCount", saved.getHosts().size()));

        return mapper.toResponse(saved, 0L);
    }

    @Override
    @Transactional
    public HostGroupResponse update(Long id, HostGroupRequest request) {
        HostGroup group = requireGroup(id);

        if (hostGroupRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("A group named '%s' already exists".formatted(request.name()));
        }

        group.setName(request.name());
        group.setDescription(request.description() == null ? "" : request.description());
        group.setHosts(normalise(request.hosts()));

        auditService.record("host_group.updated", "host_group", id,
                Map.of("hostCount", group.getHosts().size()));

        return mapper.toResponse(group, actionRepository.countByHostGroupId(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        HostGroup group = requireGroup(id);
        long usage = actionRepository.countByHostGroupId(id);

        hostGroupRepository.delete(group);
        auditService.record("host_group.deleted", "host_group", id,
                Map.of("name", group.getName(), "orphanedActions", usage));
    }

    private HostGroup requireGroup(Long id) {
        return hostGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    /** Trims, drops blanks and removes duplicates while preserving the given order. */
    private List<String> normalise(List<String> hosts) {
        if (hosts == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(hosts.stream()
                .filter(host -> host != null && !host.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }
}
