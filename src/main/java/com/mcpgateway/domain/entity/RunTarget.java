package com.mcpgateway.domain.entity;

import com.mcpgateway.domain.enums.TargetStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Per-host outcome of a run. Kept as rows rather than a JSON array because each
 * host advances at its own pace and its row is updated independently.
 */
@Entity
@Table(name = "run_targets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private Run run;

    @Column(nullable = false)
    private String address;

    /** Which rolling batch this host belonged to. */
    @Builder.Default
    @Column(name = "batch_index", nullable = false)
    private int batchIndex = 0;

    @Builder.Default
    @Column(nullable = false)
    private TargetStatus status = TargetStatus.PENDING;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "stdout_excerpt")
    private String stdoutExcerpt;

    @Column(name = "stderr_excerpt")
    private String stderrExcerpt;

    /**
     * A query's answer, as rows.
     *
     * <p>Kept apart from {@code stdoutExcerpt}, which is where a command's printed output
     * goes. Flattening rows into that column meant the panel could only show aligned
     * spaces; the columns have to stay separate for anything to draw a table from them.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_rows", columnDefinition = "jsonb")
    private List<Map<String, Object>> resultRows;

    /**
     * The output of an executed run, sealed by mcp-cipher.
     *
     * <p>Holds the rendered text and the rows together. They used to sit in
     * {@link #stdoutExcerpt} and {@link #resultRows} in the clear, which turned this table
     * into a copy of whatever production data anybody had queried. Those two columns are
     * still read for rows written before this existed, and for schema reads, which carry
     * table definitions rather than data and which the planner has to read back.
     */
    @Column(name = "output_sealed")
    private byte[] outputSealed;

    @Column(name = "output_key_id")
    private String outputKeyId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
