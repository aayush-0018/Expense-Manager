package com.iconcile.expense.service;

import com.iconcile.expense.config.AnomalyProperties;
import com.iconcile.expense.repository.ExpenseRepository;
import com.iconcile.expense.repository.projection.DashboardProjections.CategoryStatsRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Flags expenses that are more than {@code multiplier}x the average for their category.
 *
 * <p>Two decisions worth stating, because the one-line requirement leaves both open:
 *
 * <ul>
 *   <li><b>The average excludes the expense being tested</b> (leave-one-out). Including it lets a
 *       large expense inflate its own baseline and suppress its own flag - the bigger the outlier,
 *       the harder it is to detect, which is backwards.</li>
 *   <li><b>Flags are stored, and the whole category is re-evaluated on every write.</b> Adding an
 *       expense moves the category average, which can flip other rows in or out of anomaly state.
 *       Without the sweep, the dashboard's anomaly count drifts away from the data.</li>
 * </ul>
 *
 * <p>The sweep is one set-based UPDATE per affected category - not a row loop - so a CSV import
 * touching six categories costs six statements regardless of row count.
 */
@Service
public class AnomalyService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyService.class);

    private final ExpenseRepository expenseRepository;
    private final AnomalyProperties properties;

    public AnomalyService(ExpenseRepository expenseRepository, AnomalyProperties properties) {
        this.expenseRepository = expenseRepository;
        this.properties = properties;
    }

    /** Re-evaluates every expense in one category. No-op for a null id. */
    @Transactional
    public void reevaluateCategory(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        int updated = expenseRepository.reevaluateAnomaliesForCategory(
                categoryId,
                properties.getMultiplier(),
                properties.getMinSampleSize(),
                properties.sinceDate(LocalDate.now()));
        if (updated > 0) {
            log.debug("Anomaly sweep for category {} changed {} row(s)", categoryId, updated);
        }
    }

    /** Re-evaluates a set of categories, de-duplicated and ignoring nulls. */
    @Transactional
    public void reevaluateCategories(Collection<Long> categoryIds) {
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long id : categoryIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        distinct.forEach(this::reevaluateCategory);
    }

    /**
     * Per-category baselines for explaining a flag in the UI ("4.2x the Food average").
     * Loaded in one query so the anomaly list does not issue a stats query per row.
     */
    @Transactional(readOnly = true)
    public Map<Long, CategoryBaseline> loadBaselines() {
        Map<Long, CategoryBaseline> baselines = new HashMap<>();
        for (CategoryStatsRow row : expenseRepository.categoryStats(properties.sinceDate(LocalDate.now()))) {
            baselines.put(row.getCategoryId(),
                    new CategoryBaseline(row.getTotalAmount(), row.getExpenseCount(), properties.getMultiplier()));
        }
        return baselines;
    }

    /**
     * Category totals, from which the leave-one-out average for any single expense in that
     * category can be derived without another query.
     */
    public record CategoryBaseline(BigDecimal total, long count, BigDecimal multiplier) {

        /** Mean of every expense in the category <em>except</em> the given one. */
        public BigDecimal averageExcluding(BigDecimal amount) {
            if (count <= 1) {
                return BigDecimal.ZERO;
            }
            return total.subtract(amount)
                    .divide(BigDecimal.valueOf(count - 1), 2, RoundingMode.HALF_UP);
        }

        /** The amount an expense would have to exceed to be flagged. */
        public BigDecimal thresholdExcluding(BigDecimal amount) {
            return averageExcluding(amount).multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        }

        /** How many times the leave-one-out average this expense is; zero when undefined. */
        public BigDecimal timesAverage(BigDecimal amount) {
            BigDecimal average = averageExcluding(amount);
            if (average.signum() == 0) {
                return BigDecimal.ZERO;
            }
            return amount.divide(average, 2, RoundingMode.HALF_UP);
        }
    }
}
