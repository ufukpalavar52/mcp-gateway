package com.mcpgateway.domain.entity;

import com.mcpgateway.domain.enums.ModelProvider;
import com.mcpgateway.domain.json.ModelHealth;
import com.mcpgateway.domain.json.ModelParams;
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
 * A language model connection: provider, model id, endpoint and API key reference.
 * This is not an MCP server — it is what a definition talks to.
 */
@Entity
@Table(name = "ai_models")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModel extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private ModelProvider provider = ModelProvider.ANTHROPIC;

    /** Identifier sent to the provider, for example {@code claude-opus-5}. */
    @Column(name = "model_id", nullable = false)
    private String modelId;

    @Column(nullable = false)
    private String endpoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_key_secret_id")
    private Secret apiKeySecret;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    private ModelParams params = new ModelParams();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "health", nullable = false, columnDefinition = "jsonb")
    private ModelHealth health = new ModelHealth();

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Column(nullable = false)
    private String notes = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
