package com.iconcile.expense.domain;

/**
 * How an expense ended up in its category.
 */
public enum CategorizationSource {
    /** A vendor rule matched. */
    RULE,
    /** No rule matched; the fallback category was used. */
    DEFAULT,
    /** The caller supplied an explicit categoryId, overriding the rule engine. */
    MANUAL_OVERRIDE
}
