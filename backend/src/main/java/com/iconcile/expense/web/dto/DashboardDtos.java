package com.iconcile.expense.web.dto;

import java.util.List;

/** Response shapes for the dashboard endpoints. Amounts are decimal strings throughout. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** Headline tiles. {@code month} is null when the window is "all time". */
    public record SummaryResponse(
            String month,
            String totalAmount,
            long expenseCount,
            long anomalyCount,
            String topCategoryName,
            String topCategoryAmount
    ) {
    }

    /**
     * Stacked-bar data. {@code months} is the complete, gap-free axis and every series has
     * exactly one total per month, zero-filled server side - the chart should not have to
     * reconcile ragged series against a shared axis.
     */
    public record MonthlyCategoryTotalsResponse(
            List<String> months,
            List<CategorySeries> series
    ) {
        public record CategorySeries(Long categoryId, String categoryName, String colorHex, List<String> totals) {
        }
    }

    public record TopVendorsResponse(List<VendorTotal> vendors) {
        public record VendorTotal(String vendorName, String totalAmount, long expenseCount, String topCategory) {
        }
    }

    /**
     * An anomalous expense together with the numbers that explain the flag, so the UI can say
     * "4.2x the Food average" rather than just showing a warning icon.
     */
    public record AnomalyItem(
            ExpenseResponse expense,
            String categoryAverage,
            String threshold,
            String timesAverage
    ) {
    }
}
