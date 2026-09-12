package com.mcpgateway.mapper;

import com.mcpgateway.domain.entity.HostGroup;
import com.mcpgateway.dto.response.HostGroupResponse;
import org.springframework.stereotype.Component;

/** Entity to response translation for host groups. */
@Component
public class HostGroupMapper {

    public HostGroupResponse toResponse(HostGroup group, long usedByCount) {
        return new HostGroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getHosts(),
                group.getHosts().size(),
                usedByCount,
                group.getUpdatedAt());
    }
}
