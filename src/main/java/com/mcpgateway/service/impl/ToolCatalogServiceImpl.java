package com.mcpgateway.service.impl;

import com.mcpgateway.common.exception.ResourceNotFoundException;
import com.mcpgateway.domain.entity.Definition;
import com.mcpgateway.dto.response.ToolResponse;
import com.mcpgateway.mapper.ToolMapper;
import com.mcpgateway.repository.DefinitionRepository;
import com.mcpgateway.service.intf.ToolCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Serves the MCP tool catalogue.
 *
 * <p>The catalogue is derived from definitions, so this service only decides which
 * definitions qualify; turning one into a tool descriptor is the mapper's job.
 */
@Service
@RequiredArgsConstructor
public class ToolCatalogServiceImpl implements ToolCatalogService {

    private final DefinitionRepository definitionRepository;
    private final ToolMapper toolMapper;
    private final com.mcpgateway.service.DefinitionAccessGuard access;

    @Override
    @Transactional(readOnly = true)
    public List<ToolResponse> findAll() {
        // Filtered rather than refused: a listing is what somebody may work with, and one
        // that showed tools that then refuse to run would be a menu of disappointments.
        return access.runnable(definitionRepository.findAll()).stream()
                .map(toolMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ToolResponse> findPublished() {
        return access.runnable(definitionRepository.findPublishedTools()).stream()
                .map(toolMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ToolResponse findByToolName(String toolName) {
        Definition definition = definitionRepository.findByToolName(toolName)
                // Not found rather than forbidden, and on purpose: to somebody with no
                // access, a tool they may not reach and a tool that does not exist are the
                // same fact. Telling them apart is a way to enumerate the catalogue.
                .filter(access::mayRun)
                .orElseThrow(() -> new ResourceNotFoundException("Tool", toolName));

        return toolMapper.toResponse(definition);
    }
}
