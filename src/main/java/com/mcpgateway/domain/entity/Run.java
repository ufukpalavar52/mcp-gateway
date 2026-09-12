package com.mcpgateway.domain.entity;

import com.mcpgateway.domain.enums.RunStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One execution of an action. The gateway owns the state machine; the Go worker
 * only reports progress, so nothing here is written by the worker directly.
 */
@Entity
@Table(name = "runs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Run {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private Definition definition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_id")
    private Action action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    /** Set for non-human callers such as {@code agent:ci-bot}. */
    @Column(name = "actor_label")
    private String actorLabel;

    @Builder.Default
    @Column(nullable = false)
    private RunStatus status = RunStatus.PENDING;

    /** Values passed at call time; secret-typed inputs are stored as null. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inputs", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> inputs = new HashMap<>();

    @Column(name = "generated_command")
    private String generatedCommand;

    @Column(name = "generated_query")
    private String generatedQuery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    private String error;

    /**
     * The executor's own identifiers for this work.
     *
     * <p>mcp-action generates a UUID per job and one per action, and reports results
     * against them. It never sees {@code id} — that is a bigint identity assigned here —
     * so without these a result arrives with no way to say which row it belongs to.
     *
     * <p>Nullable: this table predates the queue, and older rows have no reference.
     */
    @Column(name = "run_ref")
    private String runRef;

    @Column(name = "action_ref")
    private String actionRef;

    /**
     * Why this ran: {@code execute} for work somebody asked for, {@code introspect} for a
     * schema this service went and read.
     *
     * <p>Results come back on the same queue and look the same, so without this there is
     * no way to tell where a schema answer should be written.
     */
    @Builder.Default
    @Column(nullable = false)
    private String purpose = "execute";


    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RunTarget> targets = new ArrayList<>();
}
