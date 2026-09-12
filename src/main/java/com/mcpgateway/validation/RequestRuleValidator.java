package com.mcpgateway.validation;

/**
 * One cohesive set of domain rules for one request type.
 *
 * <p>Implementations are Spring beans discovered by the validation aspect. Each covers
 * a single request type and nothing else, so a new rule set never forces a change in
 * the aspect or in any sibling validator.
 *
 * @param <T> the request type this validator understands
 */
public interface RequestRuleValidator<T> {

    /** The request type this validator applies to. */
    Class<T> supportedType();

    /** Reports every broken rule into {@code violations}; never throws for a rule failure. */
    void validate(T request, RuleViolations violations);
}
