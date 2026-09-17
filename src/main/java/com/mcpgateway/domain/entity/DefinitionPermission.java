package com.mcpgateway.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One person's standing with one definition.
 *
 * <p>Only consulted when the definition is {@code RESTRICTED}; an open one needs no rows at
 * all, which is why every definition that existed before this table did still works.
 *
 * <p>{@code canEdit} implies {@code canRun} wherever this is read. Somebody who may change
 * the command may obviously run the command they changed, and keeping the two apart would
 * describe a restriction that does not exist.
 */
@Entity
@Table(name = "definition_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "definition_permissions_unique",
                columnNames = {"definition_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DefinitionPermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private Definition definition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Column(name = "can_run", nullable = false)
    private boolean canRun = true;

    @Builder.Default
    @Column(name = "can_edit", nullable = false)
    private boolean canEdit = false;
}
