package com.mcpgateway.service.intf;

import com.mcpgateway.dto.response.ToolResponse;

import java.util.List;

/**
 * The tool surface the MCP server exposes.
 *
 * <p>Read only by design: the catalogue is derived from definitions, so there is
 * nothing here to create or update.
 */
public interface ToolCatalogService {

    /** Every definition, including disabled ones, for the panel's tools page. */
    List<ToolResponse> findAll();

    /** Only publishable tools; this is what {@code tools/list} should return. */
    List<ToolResponse> findPublished();

    ToolResponse findByToolName(String toolName);
}
