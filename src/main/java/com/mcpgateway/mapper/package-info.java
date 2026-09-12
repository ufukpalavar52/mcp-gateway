/**
 * Entity to response translation.
 *
 * <p>One mapper per resource family, each a stateless {@code @Component} exposing
 * {@code toResponse} overloads. Extra arguments are allowed where a response needs
 * something the entity does not hold, such as a usage count or a one-time token.
 *
 * <p>Two rules keep the layer predictable:
 *
 * <ul>
 *   <li><strong>Mapping only.</strong> No repository calls and no business decisions.
 *       Anything a mapper would have to reason about belongs to the domain instead;
 *       {@code Action.resolveTargetCount()} is there for that reason.</li>
 *   <li><strong>The reverse direction is not here.</strong> Turning a request into an
 *       entity needs to resolve references and enforce rules, so it stays in the
 *       service where those dependencies already live.</li>
 * </ul>
 */
package com.mcpgateway.mapper;
