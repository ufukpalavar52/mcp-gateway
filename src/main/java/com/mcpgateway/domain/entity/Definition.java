package com.mcpgateway.domain.entity;

import com.mcpgateway.domain.enums.DefinitionAccess;
import com.mcpgateway.domain.json.DefinitionInput;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
 * A definition is exactly one tool exposed by the MCP server.
 *
 * <p>{@code toolName} and {@code toolDescription} are what {@code tools/list} returns;
 * {@code inputs} becomes the tool's JSON Schema; {@code actions} are what runs when
 * {@code tools/call} arrives.
 */
@Entity
@Table(name = "definitions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Definition extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    /** MCP tool identifier, unique across the catalogue. */
    @Column(name = "tool_name", nullable = false, unique = true)
    private String toolName;

    @Builder.Default
    @Column(name = "tool_description", nullable = false)
    private String toolDescription = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    private AiModel model;

    @Builder.Default
    @Column(name = "system_prompt", nullable = false)
    private String systemPrompt = "";

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inputs", nullable = false, columnDefinition = "jsonb")
    private List<DefinitionInput> inputs = new ArrayList<>();

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Whether everybody may reach this definition, or only the people named on it.
     *
     * <p>Defaults to OPEN, which is what every definition written before this field existed
     * is — adding the column changed nothing about who could do what.
     */
    @Builder.Default
    @Column(nullable = false)
    private DefinitionAccess access = DefinitionAccess.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Builder.Default
    @OrderBy("position ASC")
    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Action> actions = new ArrayList<>();

    /** Keeps both sides of the association in sync and assigns the ordering slot. */
    public void addAction(Action action) {
        action.setDefinition(this);
        action.setPosition(actions.size());
        actions.add(action);
    }

    /** Drops every action; used when a definition is replaced wholesale. */
    public void clearActions() {
        actions.clear();
    }
}
