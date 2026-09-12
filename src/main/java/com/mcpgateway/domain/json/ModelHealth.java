package com.mcpgateway.domain.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mcpgateway.domain.enums.ModelStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Result of the latest connectivity probe, stored in {@code ai_models.health}.
 * Only the most recent attempt is kept; history would need its own table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelHealth {

    @Builder.Default
    private ModelStatus status = ModelStatus.UNCHECKED;

    private Integer latencyMs;

    private Instant checkedAt;

    private String error;
}
