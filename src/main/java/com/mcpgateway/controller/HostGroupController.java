package com.mcpgateway.controller;

import com.mcpgateway.dto.request.HostGroupRequest;
import com.mcpgateway.dto.response.HostGroupResponse;
import com.mcpgateway.service.intf.HostGroupService;
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

/** Host inventory used as SSH targets. */
@RestController
@RequestMapping("/api/v1/host-groups")
@RequiredArgsConstructor
public class HostGroupController {

    private final HostGroupService hostGroupService;

    @GetMapping
    public ResponseEntity<List<HostGroupResponse>> findAll() {
        return ResponseEntity.ok(hostGroupService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<HostGroupResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(hostGroupService.findById(id));
    }

    @PostMapping
    @ValidateBusinessRules
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<HostGroupResponse> create(@Valid @RequestBody HostGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hostGroupService.create(request));
    }

    @PutMapping("/{id}")
    @ValidateBusinessRules
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<HostGroupResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody HostGroupRequest request) {
        return ResponseEntity.ok(hostGroupService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        hostGroupService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
