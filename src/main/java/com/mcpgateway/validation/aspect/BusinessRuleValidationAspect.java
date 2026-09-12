package com.mcpgateway.validation.aspect;

import com.mcpgateway.common.dto.ApiError;
import com.mcpgateway.validation.BusinessRuleValidationException;
import com.mcpgateway.validation.RequestRuleValidator;
import com.mcpgateway.validation.RuleViolations;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs domain rule validators before a method annotated with
 * {@link com.mcpgateway.validation.annotation.ValidateBusinessRules} executes.
 *
 * <p>The aspect owns no rules itself. It resolves a {@link RequestRuleValidator} by
 * argument type and delegates, which keeps it closed for modification while the rule
 * set stays open for extension. Validators are indexed once at construction, so the
 * per-request cost is a map lookup.
 *
 * <p>Every argument is checked and all violations are merged, so one response can
 * describe every problem in the request.
 */
@Slf4j
@Aspect
@Component
public class BusinessRuleValidationAspect {

    private final Map<Class<?>, List<RequestRuleValidator<?>>> validatorsByType = new HashMap<>();

    public BusinessRuleValidationAspect(List<RequestRuleValidator<?>> validators) {
        validators.forEach(validator -> validatorsByType
                .computeIfAbsent(validator.supportedType(), key -> new ArrayList<>())
                .add(validator));

        log.info("Business rule validation active for {} request type(s)", validatorsByType.size());
    }

    @Before("@annotation(com.mcpgateway.validation.annotation.ValidateBusinessRules)")
    public void validateArguments(JoinPoint joinPoint) {
        RuleViolations violations = new RuleViolations();

        for (Object argument : joinPoint.getArgs()) {
            if (argument != null) {
                applyValidators(argument, violations);
            }
        }

        if (!violations.isEmpty()) {
            List<ApiError.FieldError> found = violations.asList();
            log.debug("Rejected {} with {} rule violation(s)",
                    joinPoint.getSignature().toShortString(), found.size());
            throw new BusinessRuleValidationException(found);
        }
    }

    /**
     * Applies every validator registered for the argument's type.
     *
     * <p>The cast is safe because a validator is only ever indexed under the exact
     * class it declares as supported.
     */
    @SuppressWarnings("unchecked")
    private <T> void applyValidators(Object argument, RuleViolations violations) {
        List<RequestRuleValidator<?>> validators = validatorsByType.get(argument.getClass());

        if (validators == null) {
            return;
        }
        for (RequestRuleValidator<?> validator : validators) {
            ((RequestRuleValidator<T>) validator).validate((T) argument, violations);
        }
    }
}
