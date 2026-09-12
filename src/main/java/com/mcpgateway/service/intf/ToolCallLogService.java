package com.mcpgateway.service.intf;

import com.mcpgateway.common.dto.PageResponse;
import com.mcpgateway.domain.enums.LogLevel;
import com.mcpgateway.dto.response.DailyCallCountResponse;
import com.mcpgateway.dto.response.ModelUsageResponse;
import com.mcpgateway.dto.response.ToolCallResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/** Read access to the tool call audit trail. */
public interface ToolCallLogService {

    PageResponse<ToolCallResponse> search(LogLevel level, String toolName, String search, Pageable pageable);

    /** Row counts per level, used by the log page's tabs. */
    Map<String, Long> countsByLevel();

    /**
     * Daily counts for the last {@code days} days.
     *
     * <p>Days without a single call are filled in with zeroes, so the chart keeps a
     * continuous x axis instead of collapsing the gaps.
     */
    List<DailyCallCountResponse> dailyCounts(int days);

    /** Call distribution per model. */
    List<ModelUsageResponse> usageByModel();
}
