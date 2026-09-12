package com.mcpgateway.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A domain rule was broken. Distinct from bean validation: these are the cross-field
 * rules that moved out of the database when the schema adopted JSONB.
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", message);
    }
}
