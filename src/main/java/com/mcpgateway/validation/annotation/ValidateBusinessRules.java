package com.mcpgateway.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose arguments must pass the domain rule validators before the
 * method body runs.
 *
 * <p>Bean validation covers the <em>shape</em> of a request: required fields, sizes,
 * patterns. It cannot express the rules this project actually needs, because they are
 * conditional across fields ("a static command must not be empty, but only when the
 * command mode is static") or need a repository lookup ("the referenced host group
 * must exist"). Those rules were database constraints until the schema moved to JSONB;
 * this annotation is where they now live.
 *
 * <p>The work is done by {@code BusinessRuleValidationAspect}, which resolves a
 * {@link com.mcpgateway.validation.RequestRuleValidator} for each argument type.
 * Adding a rule therefore means adding a bean, never editing the aspect.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidateBusinessRules {
}
