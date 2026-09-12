package com.mcpgateway.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * Provider specific request parameters.
 *
 * <p>Which fields are legal depends on the provider, so the mutually exclusive rules
 * are checked by the business rule validator rather than by field constraints.
 */
public record ModelParamsRequest(

        @Min(value = 1, message = "maxTokens must be positive")
        Integer maxTokens,

        /** low | medium | high | xhigh | max — Anthropic only. */
        String effort,

        /** adaptive | disabled — Anthropic only. */
        String thinking,

        @DecimalMin(value = "0.0", message = "temperature must be at least 0")
        @DecimalMax(value = "2.0", message = "temperature must be at most 2")
        BigDecimal temperature,

        @Min(value = 1000, message = "timeoutMs must be at least 1000")
        Integer timeoutMs) {
}
