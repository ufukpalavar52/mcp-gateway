package com.mcpgateway.service.intf;

import com.mcpgateway.dto.request.DefinitionRequest;
import com.mcpgateway.dto.response.DefinitionResponse;
import com.mcpgateway.dto.response.DefinitionSummaryResponse;
import org.springframework.data.domain.Pageable;

import com.mcpgateway.common.dto.PageResponse;

/** Definitions, each of which is one MCP tool. */
public interface DefinitionService {

    PageResponse<DefinitionSummaryResponse> findAll(Pageable pageable);

    DefinitionResponse findById(Long id);

    DefinitionResponse create(DefinitionRequest request);

    DefinitionResponse update(Long id, DefinitionRequest request);

    DefinitionResponse toggleEnabled(Long id);

    /** Copies a definition, giving the copy a free name and tool name. */
    DefinitionResponse duplicate(Long id);

    void delete(Long id);
}
