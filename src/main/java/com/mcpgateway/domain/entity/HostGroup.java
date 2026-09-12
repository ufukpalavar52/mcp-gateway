package com.mcpgateway.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * The host inventory an SSH action targets. A group is a plain address list, so it
 * is stored as a JSON array rather than a join table.
 */
@Entity
@Table(name = "host_groups")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostGroup extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private String description = "";

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hosts", nullable = false, columnDefinition = "jsonb")
    private List<String> hosts = new ArrayList<>();
}
