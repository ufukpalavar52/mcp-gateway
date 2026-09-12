package com.mcpgateway.controller;

import com.mcpgateway.dto.response.ToolResponse;
import com.mcpgateway.service.intf.ToolCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The MCP tool catalogue, derived from definitions and therefore read only.
 *
 * <p>{@code GET /api/v1/tools?published=true} is what the Python MCP server calls to
 * answer {@code tools/list}: the payload already carries a usable JSON Schema, so it
 * can be forwarded without further shaping.
 */
@RestController
@RequestMapping("/api/v1/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolCatalogService toolCatalogService;

    @GetMapping
    public ResponseEntity<List<ToolResponse>> findAll(
            @RequestParam(defaultValue = "false") boolean published) {

        return ResponseEntity.ok(published
                ? toolCatalogService.findPublished()
                : toolCatalogService.findAll());
    }

    @GetMapping("/{toolName}")
    public ResponseEntity<ToolResponse> findByToolName(@PathVariable String toolName) {
        return ResponseEntity.ok(toolCatalogService.findByToolName(toolName));
    }
}
