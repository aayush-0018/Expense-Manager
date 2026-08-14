package com.iconcile.expense.service;

import com.iconcile.expense.AbstractIntegrationTest;
import com.iconcile.expense.domain.Expense;
import com.iconcile.expense.repository.CategoryRepository;
import com.iconcile.expense.web.dto.ExpenseRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the anomaly rule against real PostgreSQL, since the rule lives in a single
 * set-based {@code UPDATE ... FROM} statement rather than in Java.
 */
class AnomalyDetectionIT extends AbstractIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 1);

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private CategoryRepository categoryRepository;

    private Long foodCategoryId() {
        return categoryRepository.findByNameIgnoreCase("Food").orElseThrow().getId();
    }

    private Expense add(String amount) {
        return expenseService.create(new ExpenseRequest(DAY, new BigDecimal(amount), "Swiggy", null, null));
    }

    private Expense reload(Long id) {
        return expenseRepository.findById(id).orElseThrow();
    }

    @Test
    @DisplayName("an expense far above the category average is flagged")
    void flagsClearOutlier() {
        add("100.00");
        add("100.00");
        add("100.00");
        Expense outlier = add("1000.00");

        assertThat(reload(outlier.getId()).isAnomaly()).isTrue();
        assertThat(reload(outlier.getId()).getAnomalyReason()).isEqualTo("AMOUNT_GT_3X_CATEGORY_AVG");
    }

    @Test
    @DisplayName("exactly 3x the average is not an anomaly; the rule is strictly greater than")
    void boundaryIsExclusive() {
        add("100.00");
        add("100.00");
        add("100.00");
        Expense atBoundary = add("300.00");

        assertThat(reload(atBoundary.getId()).isAnomaly()).isFalse();
    }

    @Test
    @DisplayName("a hair over 3x is an anomaly")
    void justAboveBoundaryIsFlagged() {
        add("100.00");
        add("100.00");
        add("100.00");
        Expense justOver = add("300.01");

        assertThat(reload(justOver.getId()).isAnomaly()).isTrue();
    }

    @Test
    @DisplayName("the baseline excludes the expense itself, so a big outlier cannot hide behind its own weight")
    void baselineIsLeaveOneOut() {
        add("100.00");
        add("100.00");
        add("100.00");
        // Including itself, the average would be (300 + 900) / 4 = 300, and 900 < 3 x 300,
        // so a naive implementation would let this through. Leave-one-out gives 100, and it is flagged.
        Expense outlier = add("900.00");

        assertThat(reload(outlier.getId()).isAnomaly()).isTrue();
    }

    @Test
    @DisplayName("a category with too few other expenses never flags anything")
    void respectsMinimumSampleSize() {
        Expense first = add("100.00");
        Expense second = add("10000.00");

        assertThat(reload(first.getId()).isAnomaly()).isFalse();
        assertThat(reload(second.getId()).isAnomaly()).isFalse();
    }

    @Test
    @DisplayName("a single expense in a category never divides by zero")
    void singleExpenseIsSafe() {
        Expense only = add("500.00");
        assertThat(reload(only.getId()).isAnomaly()).isFalse();
    }

    @Test
    @DisplayName("identical amounts are never outliers")
    void identicalAmountsAreNeverFlagged() {
        List<Expense> all = List.of(add("100.00"), add("100.00"), add("100.00"), add("100.00"), add("100.00"));
        assertThat(all).allSatisfy(expense -> assertThat(reload(expense.getId()).isAnomaly()).isFalse());
    }

    @Test
    @DisplayName("adding comparable expenses raises the average and clears an existing flag")
    void newExpensesCanClearAFlag() {
        add("100.00");
        add("100.00");
        add("100.00");
        Expense outlier = add("1000.00");
        assertThat(reload(outlier.getId()).isAnomaly()).isTrue();

        add("900.00");
        add("950.00");
        add("1100.00");

        assertThat(reload(outlier.getId()).isAnomaly())
                .as("the category average moved; the flag must follow the data")
                .isFalse();
    }

    @Test
    @DisplayName("deleting the expenses that raised the average restores the flag")
    void deletingRestoresAFlag() {
        add("100.00");
        add("100.00");
        add("100.00");
        Expense outlier = add("1000.00");
        Expense filler1 = add("900.00");
        Expense filler2 = add("950.00");
        Expense filler3 = add("1100.00");
        assertThat(reload(outlier.getId()).isAnomaly()).isFalse();

        expenseService.delete(filler1.getId());
        expenseService.delete(filler2.getId());
        expenseService.delete(filler3.getId());

        assertThat(reload(outlier.getId()).isAnomaly()).isTrue();
    }

    @Test
    @DisplayName("editing an amount re-evaluates both the old and the new category")
    void updateSweepsBothCategories() {
        add("100.00");
        add("100.00");
        add("100.00");
        Expense outlier = add("1000.00");
        assertThat(reload(outlier.getId()).isAnomaly()).isTrue();

        // Move it into Travel, which has too few rows to flag anything.
        Long travelId = categoryRepository.findByNameIgnoreCase("Travel").orElseThrow().getId();
        expenseService.update(outlier.getId(),
                new ExpenseRequest(DAY, new BigDecimal("1000.00"), "Swiggy", null, travelId));

        Expense moved = reload(outlier.getId());
        assertThat(moved.getCategory().getId()).isEqualTo(travelId);
        assertThat(moved.isAnomaly()).as("Travel has no baseline yet").isFalse();
    }

    @Test
    @DisplayName("categories are evaluated independently")
    void categoriesDoNotBleedIntoEachOther() {
        add("100.00");
        add("100.00");
        add("100.00");
        Expense foodOutlier = add("1000.00");

        // Travel expenses are large, but that must not change how Food is judged.
        for (int i = 0; i < 4; i++) {
            expenseService.create(new ExpenseRequest(DAY, new BigDecimal("5000.00"), "Uber", null, null));
        }

        assertThat(reload(foodOutlier.getId()).isAnomaly()).isTrue();
        assertThat(reload(foodOutlier.getId()).getCategory().getId()).isEqualTo(foodCategoryId());
    }
}
