package com.iconcile.expense.service;

import com.iconcile.expense.config.AnomalyProperties;
import com.iconcile.expense.domain.Expense;
import com.iconcile.expense.repository.ExpenseRepository;
import com.iconcile.expense.repository.ExpenseSpecifications;
import com.iconcile.expense.repository.projection.DashboardProjections.CategoryTotalRow;
import com.iconcile.expense.repository.projection.DashboardProjections.MonthlyCategoryTotalRow;
import com.iconcile.expense.repository.projection.DashboardProjections.SummaryRow;
import com.iconcile.expense.repository.projection.DashboardProjections.VendorTotalRow;
import com.iconcile.expense.service.AnomalyService.CategoryBaseline;
import com.iconcile.expense.web.dto.DashboardDtos.AnomalyItem;
import com.iconcile.expense.web.dto.DashboardDtos.MonthlyCategoryTotalsResponse;
import com.iconcile.expense.web.dto.DashboardDtos.SummaryResponse;
import com.iconcile.expense.web.dto.DashboardDtos.TopVendorsResponse;
import com.iconcile.expense.web.dto.ExpenseResponse;
import com.iconcile.expense.web.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    /** Upper bound used instead of a null date parameter, keeping the native queries simple. */
    private static final LocalDate END_OF_TIME = LocalDate.of(2999, 12, 31);

    private final ExpenseRepository expenseRepository;
    private final AnomalyService anomalyService;

    public DashboardService(ExpenseRepository expenseRepository, AnomalyService anomalyService) {
        this.expenseRepository = expenseRepository;
        this.anomalyService = anomalyService;
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(YearMonth month) {
        LocalDate from = month == null ? AnomalyProperties.BEGINNING_OF_TIME : month.atDay(1);
        LocalDate to = month == null ? END_OF_TIME : month.atEndOfMonth();

        SummaryRow row = expenseRepository.summarize(from, to);
        List<CategoryTotalRow> byCategory = expenseRepository.categoryTotals(from, to);
        CategoryTotalRow top = byCategory.isEmpty() ? null : byCategory.get(0);

        return new SummaryResponse(
                month == null ? null : month.toString(),
                money(row.getTotalAmount()),
                row.getExpenseCount(),
                row.getAnomalyCount(),
                top == null ? null : top.getCategoryName(),
                top == null ? null : money(top.getTotalAmount()));
    }

    /**
     * Monthly totals per category over an inclusive month range.
     *
     * <p>The month axis is generated from the range rather than from the data, and every series
     * is padded to that axis, so a category with no spend in March still has a March entry.
     */
    @Transactional(readOnly = true)
    public MonthlyCategoryTotalsResponse monthlyTotalsByCategory(YearMonth from, YearMonth to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' month " + from + " is after 'to' month " + to);
        }

        List<String> months = new ArrayList<>();
        for (YearMonth cursor = from; !cursor.isAfter(to); cursor = cursor.plusMonths(1)) {
            months.add(cursor.toString());
        }
        Map<String, Integer> monthIndex = new LinkedHashMap<>();
        for (int i = 0; i < months.size(); i++) {
            monthIndex.put(months.get(i), i);
        }

        Map<Long, SeriesBuilder> builders = new LinkedHashMap<>();
        for (MonthlyCategoryTotalRow row : expenseRepository.monthlyTotalsByCategory(from.atDay(1), to.atEndOfMonth())) {
            SeriesBuilder builder = builders.computeIfAbsent(row.getCategoryId(),
                    id -> new SeriesBuilder(id, row.getCategoryName(), row.getColorHex(), months.size()));
            Integer index = monthIndex.get(row.getYearMonth());
            if (index != null) {
                builder.totals[index] = row.getTotalAmount();
            }
        }

        List<MonthlyCategoryTotalsResponse.CategorySeries> series = builders.values().stream()
                .sorted((a, b) -> b.sum().compareTo(a.sum()))
                .map(SeriesBuilder::build)
                .toList();

        return new MonthlyCategoryTotalsResponse(months, series);
    }

    @Transactional(readOnly = true)
    public TopVendorsResponse topVendors(YearMonth month, int limit) {
        LocalDate from = month == null ? AnomalyProperties.BEGINNING_OF_TIME : month.atDay(1);
        LocalDate to = month == null ? END_OF_TIME : month.atEndOfMonth();

        List<TopVendorsResponse.VendorTotal> vendors = new ArrayList<>();
        for (VendorTotalRow row : expenseRepository.topVendorsBySpend(from, to, limit)) {
            vendors.add(new TopVendorsResponse.VendorTotal(
                    row.getVendorName(), money(row.getTotalAmount()), row.getExpenseCount(), row.getTopCategory()));
        }
        return new TopVendorsResponse(vendors);
    }

    /**
     * The anomaly list, each row carrying its own baseline. Baselines come from one grouped
     * query for all categories, so the page costs two queries regardless of its size.
     */
    @Transactional(readOnly = true)
    public PageResponse<AnomalyItem> anomalies(Pageable pageable) {
        Page<Expense> page = expenseRepository.findAll(
                ExpenseSpecifications.withFilters(null, null, null, null, true), pageable);
        Map<Long, CategoryBaseline> baselines = anomalyService.loadBaselines();

        List<AnomalyItem> items = page.getContent().stream().map(expense -> {
            CategoryBaseline baseline = baselines.get(expense.getCategory().getId());
            if (baseline == null) {
                return new AnomalyItem(ExpenseResponse.from(expense), "0.00", "0.00", "0.00");
            }
            return new AnomalyItem(
                    ExpenseResponse.from(expense),
                    money(baseline.averageExcluding(expense.getAmount())),
                    money(baseline.thresholdExcluding(expense.getAmount())),
                    money(baseline.timesAverage(expense.getAmount())));
        }).toList();

        return PageResponse.of(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static final class SeriesBuilder {
        private final Long categoryId;
        private final String categoryName;
        private final String colorHex;
        private final BigDecimal[] totals;

        private SeriesBuilder(Long categoryId, String categoryName, String colorHex, int monthCount) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.colorHex = colorHex;
            this.totals = new BigDecimal[monthCount];
        }

        private BigDecimal sum() {
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal total : totals) {
                if (total != null) {
                    sum = sum.add(total);
                }
            }
            return sum;
        }

        private MonthlyCategoryTotalsResponse.CategorySeries build() {
            List<String> values = new ArrayList<>(totals.length);
            for (BigDecimal total : totals) {
                values.add(money(total));
            }
            return new MonthlyCategoryTotalsResponse.CategorySeries(categoryId, categoryName, colorHex, values);
        }
    }
}
