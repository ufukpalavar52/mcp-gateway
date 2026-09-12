package com.mcpgateway.service.impl;

import com.mcpgateway.common.dto.PageResponse;
import com.mcpgateway.domain.entity.ToolCall;
import com.mcpgateway.domain.enums.LogLevel;
import com.mcpgateway.dto.response.DailyCallCountResponse;
import com.mcpgateway.dto.response.ModelUsageResponse;
import com.mcpgateway.dto.response.ToolCallResponse;
import com.mcpgateway.mapper.ToolCallMapper;
import com.mcpgateway.repository.ToolCallRepository;
import com.mcpgateway.service.intf.ToolCallLogService;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Log queries. Blank filters are normalised to null so the query can ignore them. */
@Service
@RequiredArgsConstructor
public class ToolCallLogServiceImpl implements ToolCallLogService {

    private final ToolCallRepository toolCallRepository;
    private final ToolCallMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ToolCallResponse> search(LogLevel level, String toolName,
                                                 String search, Pageable pageable) {
        return PageResponse.from(
                toolCallRepository.findAll(
                        filter(level, blankToNull(toolName), blankToNull(search)), pageable),
                mapper::toResponse);
    }

    /**
     * The filters the caller actually set.
     *
     * <p>Only those. An unset filter contributes no predicate at all, rather than a
     * parameter carrying null that the database then has to make sense of — which it could
     * not, and answered every log listing with a 503 for it.
     */
    private Specification<ToolCall> filter(LogLevel level, String toolName, String search) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (level != null) {
                predicates.add(builder.equal(root.get("level"), level));
            }
            if (toolName != null) {
                predicates.add(builder.equal(root.get("toolName"), toolName));
            }
            if (search != null) {
                String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";

                // Both columns, because a search box over a log is used to find a message
                // and to find who ran something, and asking which was meant is worse than
                // matching either.
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("message")), pattern),
                        builder.like(builder.lower(root.get("actorLabel")), pattern)));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsByLevel() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", toolCallRepository.count());

        for (LogLevel level : LogLevel.values()) {
            counts.put(level.wireValue(), toolCallRepository.countByLevel(level));
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyCallCountResponse> dailyCounts(int days) {
        int window = Math.clamp(days, 1, 90);
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(window - 1L);

        Map<LocalDate, DailyCallCountResponse> byDay = toolCallRepository
                .dailyCounts(from.atStartOfDay(ZoneOffset.UTC).toInstant())
                .stream()
                .map(this::toDailyCount)
                .collect(Collectors.toMap(DailyCallCountResponse::day, row -> row));

        // Fill the gaps so the chart's x axis stays continuous.
        return IntStream.range(0, window)
                .mapToObj(from::plusDays)
                .map(day -> byDay.getOrDefault(day, new DailyCallCountResponse(day, 0, 0)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModelUsageResponse> usageByModel() {
        return toolCallRepository.countsByModel().stream()
                .map(row -> new ModelUsageResponse((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    /** The native query returns a date and two counts; their exact types vary by driver. */
    private DailyCallCountResponse toDailyCount(Object[] row) {
        LocalDate day = row[0] instanceof java.sql.Date date
                ? date.toLocalDate()
                : LocalDate.parse(row[0].toString());

        return new DailyCallCountResponse(
                day, ((Number) row[1]).longValue(), ((Number) row[2]).longValue());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
