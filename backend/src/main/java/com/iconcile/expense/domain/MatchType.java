package com.iconcile.expense.domain;

/**
 * How a {@link VendorCategoryRule} pattern is compared against a normalized vendor name.
 */
public enum MatchType {
    /** The normalized vendor name equals the pattern. */
    EXACT,
    /** The normalized vendor name contains the pattern as a substring. */
    CONTAINS
}
