package com.mcpgateway.validation;

import com.mcpgateway.common.dto.ApiError;

import java.util.ArrayList;
import java.util.List;

/**
 * Collector handed to each rule validator.
 *
 * <p>Rules report into it instead of throwing, so a single request reports every
 * problem at once rather than making the caller fix them one round trip at a time.
 */
public class RuleViolations {

    private final List<ApiError.FieldError> violations = new ArrayList<>();

    /** Records a violation for a specific field path. */
    public void add(String field, String message) {
        violations.add(new ApiError.FieldError(field, message));
    }

    /** Records the violation only when the condition holds. */
    public void addIf(boolean condition, String field, String message) {
        if (condition) {
            add(field, message);
        }
    }

    public boolean isEmpty() {
        return violations.isEmpty();
    }

    public List<ApiError.FieldError> asList() {
        return List.copyOf(violations);
    }

    /** Flattened form used when a single message is needed. */
    public String describe() {
        return violations.stream()
                .map(violation -> violation.field() + ": " + violation.message())
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
    }
}
