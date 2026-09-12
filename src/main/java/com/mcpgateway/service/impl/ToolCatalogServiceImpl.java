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

    @Override
    @Transactional(readOnly = true)
    public List<ToolResponse> findAll() {
        return definitionRepository.findAll().stream().map(toolMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ToolResponse> findPublished() {
        return definitionRepository.findPublishedTools().stream().map(toolMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ToolResponse findByToolName(String toolName) {
        Definition definition = definitionRepository.findByToolName(toolName)
                .orElseThrow(() -> new ResourceNotFoundException("Tool", toolName));

        return toolMapper.toResponse(definition);
    }
}
