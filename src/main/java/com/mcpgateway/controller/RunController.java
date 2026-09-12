package com.mcpgateway.controller;

import com.mcpgateway.common.dto.PageResponse;
import com.mcpgateway.dto.response.RunResponse;
import com.mcpgateway.service.intf.RunService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Dispatched work: what ran, and stopping what is still running. */
@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
public class RunController {

    private final RunService runService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<RunResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        return ResponseEntity.ok(runService.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))));
    }

    @GetMapping("/{runRef}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RunResponse> get(@PathVariable String runRef) {
        return ResponseEntity.ok(runService.findByRef(runRef));
    }

    /**
     * Asks the executors to stop a run.
     *
     * <p>Answers with how many actions the request covers, not with whether they stopped.
     * The outcome arrives the usual way, as a result on the queue.
     */
    @PostMapping("/{runRef}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<Map<String, Integer>> cancel(
            @PathVariable String runRef,
            @RequestBody(required = false) Map<String, String> body) {

        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(Map.of("requested", runService.cancel(runRef, reason)));
    }
}
