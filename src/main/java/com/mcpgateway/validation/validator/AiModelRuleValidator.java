package com.mcpgateway.validation.validator;

import com.mcpgateway.domain.enums.ModelProvider;
import com.mcpgateway.dto.request.AiModelRequest;
import com.mcpgateway.dto.request.ModelParamsRequest;
import com.mcpgateway.validation.RequestRuleValidator;
import com.mcpgateway.validation.RuleViolations;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Provider dependent rules for an AI model connection.
 *
 * <p>The important one: Claude 4.6 and later removed sampling parameters. Sending
 * {@code temperature} makes the provider answer 400, so it is rejected here rather
 * than at call time. Depth is expressed with {@code effort} instead.
 */
@Component
public class AiModelRuleValidator implements RequestRuleValidator<AiModelRequest> {

    private static final Set<String> EFFORT_LEVELS = Set.of("low", "medium", "high", "xhigh", "max");
    private static final Set<String> THINKING_MODES = Set.of("adaptive", "disabled");

    @Override
    public Class<AiModelRequest> supportedType() {
        return AiModelRequest.class;
    }

    @Override
    public void validate(AiModelRequest request, RuleViolations violations) {
        ModelParamsRequest params = request.params();

        if (params == null) {
            return;
        }
        boolean anthropic = request.provider() == ModelProvider.ANTHROPIC;

        if (anthropic) {
            validateAnthropic(params, violations);
        } else {
            validateGenericProvider(params, violations);
        }
    }

    private void validateAnthropic(ModelParamsRequest params, RuleViolations violations) {
        violations.addIf(params.temperature() != null, "params.temperature",
                "Anthropic models reject sampling parameters; use effort instead");

        violations.addIf(params.effort() != null && !EFFORT_LEVELS.contains(params.effort()),
                "params.effort", "effort must be one of " + EFFORT_LEVELS);

        violations.addIf(params.thinking() != null && !THINKING_MODES.contains(params.thinking()),
                "params.thinking", "thinking must be one of " + THINKING_MODES);
    }

    private void validateGenericProvider(ModelParamsRequest params, RuleViolations violations) {
        violations.addIf(params.effort() != null, "params.effort",
                "effort is only supported by the Anthropic provider");

        violations.addIf(params.thinking() != null, "params.thinking",
                "thinking is only supported by the Anthropic provider");
    }
}
