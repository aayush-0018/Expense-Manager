package com.iconcile.expense.service;

import com.iconcile.expense.service.AnomalyService.CategoryBaseline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The arithmetic behind the numbers the UI shows next to a flagged expense. The flagging
 * itself is a set-based SQL statement and is covered by {@code AnomalyDetectionIT}.
 */
class AnomalyBaselineTest {

    private static final BigDecimal THREE = new BigDecimal("3.0");

    @Test
    @DisplayName("the baseline excludes the expense being explained")
    void averageIsLeaveOneOut() {
        // Four expenses of 100 plus one of 1000 -> total 1400, count 5.
        CategoryBaseline baseline = new CategoryBaseline(new BigDecimal("1400.00"), 5, THREE);

        // For the outlier, the others average 100 - not 280, which is what including it would give.
        assertThat(baseline.averageExcluding(new BigDecimal("1000.00"))).isEqualByComparingTo("100.00");
        assertThat(baseline.thresholdExcluding(new BigDecimal("1000.00"))).isEqualByComparingTo("300.00");
        assertThat(baseline.timesAverage(new BigDecimal("1000.00"))).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("a category holding one expense has no baseline to compare against")
    void singleExpenseCategoryHasNoBaseline() {
        CategoryBaseline baseline = new CategoryBaseline(new BigDecimal("500.00"), 1, THREE);
        assertThat(baseline.averageExcluding(new BigDecimal("500.00"))).isEqualByComparingTo("0.00");
        assertThat(baseline.timesAverage(new BigDecimal("500.00"))).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("identical expenses sit exactly at 1x the average")
    void identicalAmountsAreNeverOutliers() {
        CategoryBaseline baseline = new CategoryBaseline(new BigDecimal("500.00"), 5, THREE);
        assertThat(baseline.timesAverage(new BigDecimal("100.00"))).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("no divide-by-zero when the other expenses sum to nothing")
    void handlesZeroAverage() {
        CategoryBaseline baseline = new CategoryBaseline(new BigDecimal("100.00"), 2, THREE);
        assertThat(baseline.averageExcluding(new BigDecimal("100.00"))).isEqualByComparingTo("0.00");
        assertThat(baseline.timesAverage(new BigDecimal("100.00"))).isEqualByComparingTo("0.00");
    }
}
