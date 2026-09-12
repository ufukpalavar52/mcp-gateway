package com.mcpgateway.repository;

import com.mcpgateway.domain.entity.ToolCall;
import com.mcpgateway.domain.enums.LogLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ToolCallRepository
        extends JpaRepository<ToolCall, Long>, JpaSpecificationExecutor<ToolCall> {

    /*
     * Log filtering is a specification rather than a query with `:x is null or ...`.
     *
     * That pattern reads well and does not work here. PostgreSQL types a parameter by
     * where it is used, and a parameter used only in `? is null` gives it nothing to go
     * on: the statement failed to prepare — "could not determine data type of parameter
     * $1" — and every listing came back 503 whether a filter was set or not. Building
     * only the predicates that apply leaves no untyped parameter to guess at.
     */

    long countByLevel(LogLevel level);

    /**
     * Daily call counts, split into successful and failed.
     *
     * <p>Native because {@code date_trunc} and the {@code filter} clause have no JPQL
     * equivalent, and doing the bucketing in Java would mean loading every row.
     */
    @Query(value = """
            select date_trunc('day', created_at)::date as day,
                   count(*) filter (where level <> 'error')  as succeeded,
                   count(*) filter (where level =  'error')  as failed
            from tool_calls
            where created_at >= :since
            group by 1
            order by 1
            """, nativeQuery = true)
    List<Object[]> dailyCounts(@Param("since") Instant since);

    /** Call distribution per model, for the dashboard's model mix. */
    @Query("""
            select c.modelLabel, count(c)
            from ToolCall c
            where c.modelLabel <> ''
            group by c.modelLabel
            order by count(c) desc
            """)
    List<Object[]> countsByModel();
}
