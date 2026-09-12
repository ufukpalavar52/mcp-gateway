package com.mcpgateway.controller;

import com.mcpgateway.common.dto.PageResponse;
import com.mcpgateway.dto.request.DefinitionRequest;
import com.mcpgateway.dto.response.DefinitionResponse;
import com.mcpgateway.dto.response.DefinitionSummaryResponse;
import com.mcpgateway.service.intf.DefinitionService;
import com.mcpgateway.service.intf.SchemaService;
import com.mcpgateway.validation.annotation.ValidateBusinessRules;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Definitions, each of which is published as one MCP tool.
 *
 * <p>Write endpoints carry {@link ValidateBusinessRules}: bean validation checks the
 * shape of the payload, and the aspect then applies the conditional rules that the
 * database can no longer express now that actions and inputs live in JSONB.
 */
@RestController
@RequestMapping("/api/v1/definitions")
@RequiredArgsConstructor
public class DefinitionController {

    private final DefinitionService definitionService;
    private final SchemaService schemaService;

    @GetMapping
    public ResponseEntity<PageResponse<DefinitionSummaryResponse>> findAll(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(definitionService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DefinitionResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(definitionService.findById(id));
    }

    @PostMapping
    @ValidateBusinessRules
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<DefinitionResponse> create(@Valid @RequestBody DefinitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(definitionService.create(request));
    }

    @PutMapping("/{id}")
    @ValidateBusinessRules
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<DefinitionResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody DefinitionRequest request) {
        return ResponseEntity.ok(definitionService.update(id, request));
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<DefinitionResponse> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(definitionService.toggleEnabled(id));
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<DefinitionResponse> duplicate(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(definitionService.duplicate(id));
    }

    /**
     * Reads the real structure of a database action's tables.
     *
     * <p>Asynchronous: the answer comes back through the executor and is written onto the
     * action. The reply is the run reference, not the schema — this endpoint asks, it does
     * not wait.
     */
    @PostMapping("/{id}/actions/{actionId}/introspect")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<Map<String, String>> introspect(@PathVariable Long id,
                                                          @PathVariable Long actionId) {
        return ResponseEntity.accepted()
                .body(Map.of("runRef", schemaService.introspect(id, actionId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        definitionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
