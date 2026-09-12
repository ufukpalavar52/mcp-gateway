package com.mcpgateway.domain.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request parameters of an AI model, stored in {@code ai_models.params}.
 *
 * <p>Anthropic models from Claude 4.6 onwards reject sampling parameters such as
 * {@code temperature}; depth is controlled with {@code effort} instead. The field
 * is therefore nullable and omitted from the JSON when unset, and the validation
 * layer rejects it for the Anthropic provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelParams {

    private Integer maxTokens;

    /** low | medium | high | xhigh | max — Anthropic only. */
    private String effort;

    /** adaptive | disabled — Anthropic only. */
    private String thinking;

    /** Sampling temperature — never set for the Anthropic provider. */
    private BigDecimal temperature;

    private Integer timeoutMs;
}
