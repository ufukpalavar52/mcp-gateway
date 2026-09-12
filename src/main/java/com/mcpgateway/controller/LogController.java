package com.mcpgateway.controller;

import com.mcpgateway.common.dto.PageResponse;
import com.mcpgateway.domain.enums.LogLevel;
import com.mcpgateway.dto.response.DailyCallCountResponse;
import com.mcpgateway.dto.response.ModelUsageResponse;
import com.mcpgateway.dto.response.ToolCallResponse;
import com.mcpgateway.service.intf.ToolCallLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Read only access to the tool call audit trail. */
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogController {

    private final ToolCallLogService toolCallLogService;

    /** All filters are optional; omitting one widens the result rather than narrowing it. */
    @GetMapping
    public ResponseEntity<PageResponse<ToolCallResponse>> search(
            @RequestParam(required = false) LogLevel level,
            @RequestParam(required = false) String tool,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(toolCallLogService.search(level, tool, search, pageable));
    }

    /** Row counts per level, used by the log page tabs. */
    @GetMapping("/counts")
    public ResponseEntity<Map<String, Long>> counts() {
        return ResponseEntity.ok(toolCallLogService.countsByLevel());
    }

    /** Daily call counts for the dashboard chart; empty days are returned as zeroes. */
    @GetMapping("/timeseries")
    public ResponseEntity<List<DailyCallCountResponse>> timeseries(
            @RequestParam(defaultValue = "14") int days) {

        return ResponseEntity.ok(toolCallLogService.dailyCounts(days));
    }

    /** Call distribution per model. */
    @GetMapping("/by-model")
    public ResponseEntity<List<ModelUsageResponse>> byModel() {
        return ResponseEntity.ok(toolCallLogService.usageByModel());
    }
}
