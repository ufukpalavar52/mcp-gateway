package com.mcpgateway.controller;

import com.mcpgateway.dto.request.AiModelRequest;
import com.mcpgateway.dto.response.AiModelResponse;
import com.mcpgateway.service.intf.AiModelService;
import com.mcpgateway.validation.annotation.ValidateBusinessRules;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

/**
 * AI model connections.
 *
 * <p>Writes are restricted to admins; every authenticated role may read, because the
 * definition editor needs the list to populate its model selector.
 */
@RestController
@RequestMapping("/api/v1/models")
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelService aiModelService;

    @GetMapping
    public ResponseEntity<List<AiModelResponse>> findAll() {
        return ResponseEntity.ok(aiModelService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiModelResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(aiModelService.findById(id));
    }

    @PostMapping
    @ValidateBusinessRules
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiModelResponse> create(@Valid @RequestBody AiModelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(aiModelService.create(request));
    }

    @PutMapping("/{id}")
    @ValidateBusinessRules
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiModelResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody AiModelRequest request) {
        return ResponseEntity.ok(aiModelService.update(id, request));
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiModelResponse> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(aiModelService.toggleEnabled(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        aiModelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
