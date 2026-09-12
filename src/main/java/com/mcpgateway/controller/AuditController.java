package com.mcpgateway.controller;

import com.mcpgateway.dto.response.AuditEventResponse;
import com.mcpgateway.service.intf.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Activity feed, newest first. Backs the dashboard's recent activity card. */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditQueryService auditQueryService;

    @GetMapping
    public ResponseEntity<List<AuditEventResponse>> recent(
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(auditQueryService.recent(limit));
    }
}
