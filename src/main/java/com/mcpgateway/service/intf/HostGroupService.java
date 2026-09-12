package com.mcpgateway.service.intf;

import com.mcpgateway.dto.request.HostGroupRequest;
import com.mcpgateway.dto.response.HostGroupResponse;

import java.util.List;

/** Inventory of hosts that SSH actions target. */
public interface HostGroupService {

    List<HostGroupResponse> findAll();

    HostGroupResponse findById(Long id);

    HostGroupResponse create(HostGroupRequest request);

    HostGroupResponse update(Long id, HostGroupRequest request);

    /**
     * Deletes the group. Actions referencing it keep working but lose their target,
     * mirroring the {@code ON DELETE SET NULL} rule in the schema.
     */
    void delete(Long id);
}
