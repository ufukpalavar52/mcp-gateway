package com.mcpgateway.domain.enums;

/**
 * Who may reach a definition.
 *
 * <p>An explicit mode rather than "empty list means everybody". With the implicit form,
 * removing the last person from a restricted definition would quietly reopen it to the
 * whole installation — and nothing on the screen would change. A permission that can be
 * lost by deleting a row is not a permission anybody can reason about.
 *
 * <p>So a {@link #RESTRICTED} definition with no permissions is closed to everyone but an
 * administrator. That is a state somebody arrived at, not an accident.
 */
public enum DefinitionAccess {

    /** Anybody who can sign in may run it. The default, and what every existing definition is. */
    OPEN,

    /** Only the people named in {@code definition_permissions}. */
    RESTRICTED
}
