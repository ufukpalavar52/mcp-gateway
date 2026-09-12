package com.mcpgateway.validation;

import com.mcpgateway.common.dto.ApiError;
import com.mcpgateway.common.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/** Raised by the validation aspect when at least one domain rule is broken. */
@Getter
public class BusinessRuleValidationException extends ApiException {

    private final transient List<ApiError.FieldError> violations;

    public BusinessRuleValidationException(List<ApiError.FieldError> violations) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                "Request violates domain rules");
        this.violations = violations;
    }
}
