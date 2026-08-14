package com.iconcile.expense.repository.projection;

import java.math.BigDecimal;

/**
 * Interface projections for the native aggregation queries in
 * {@link com.iconcile.expense.repository.ExpenseRepository}.
 *
 * <p>The native queries alias every selected column with a quoted camelCase label so that
 * PostgreSQL preserves the case and Spring Data can bind it to the getters below without
 * relying on naming-strategy guesswork.
 */
public final class DashboardProjections {

    private DashboardProjections() {
    }

    /** Headline numbers for a date window. */
    public interface SummaryRow {
        BigDecimal getTotalAmount();

        long getExpenseCount();

        long getAnomalyCount();
    }

    /** One (month, category) cell of the stacked bar chart. */
    public interface MonthlyCategoryTotalRow {
        String getYearMonth();

        Long getCategoryId();

        String getCategoryName();

        String getColorHex();

        BigDecimal getTotalAmount();
    }

    /** One bar of the top-vendors chart. */
    public interface VendorTotalRow {
        String getVendorName();

        String getTopCategory();

        BigDecimal getTotalAmount();

        long getExpenseCount();
    }

    /** Per-category sum/count, used to explain why an expense was flagged. */
    public interface CategoryStatsRow {
        Long getCategoryId();

        BigDecimal getTotalAmount();

        long getExpenseCount();
    }

    /** Category total for a window, used for the "top category" tile. */
    public interface CategoryTotalRow {
        Long getCategoryId();

        String getCategoryName();

        BigDecimal getTotalAmount();
    }
}
