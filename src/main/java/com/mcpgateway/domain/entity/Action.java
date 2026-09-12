package com.mcpgateway.domain.entity;

import com.mcpgateway.domain.enums.ActionKind;
import com.mcpgateway.domain.json.ActionConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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

/**
 * One step of a definition. Type-specific settings live in {@code config}; only the
 * host group stays a real column, because deleting a group must clear the target.
 */
@Entity
@Table(name = "actions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Action extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private Definition definition;

    @Column(nullable = false)
    private ActionKind kind;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private String description = "";

    @Builder.Default
    @Column(nullable = false)
    private int position = 0;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    private ActionConfig config = new ActionConfig();

    /** Only set when {@link #kind} is {@link ActionKind#SSH} and the target is a group. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_group_id")
    private HostGroup hostGroup;

    /**
     * How many hosts this action would actually touch.
     *
     * <p>A group target counts the group's members, a list target counts its own
     * entries, a single target is one host.
     */
    public int resolveTargetCount() {
        int groupSize = hostGroup == null ? 0 : hostGroup.getHosts().size();
        return config == null ? 0 : config.resolveTargetCount(groupSize);
    }
}
