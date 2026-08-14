package com.iconcile.expense.service;

import com.iconcile.expense.AbstractIntegrationTest;
import com.iconcile.expense.web.dto.DashboardDtos.MonthlyCategoryTotalsResponse;
import com.iconcile.expense.web.dto.DashboardDtos.SummaryResponse;
import com.iconcile.expense.web.dto.DashboardDtos.TopVendorsResponse;
import com.iconcile.expense.web.dto.ExpenseRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardServiceIT extends AbstractIntegrationTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private ExpenseService expenseService;

    private void add(String date, String amount, String vendor) {
        expenseService.create(new ExpenseRequest(LocalDate.parse(date), new BigDecimal(amount), vendor, null, null));
    }

    @Test
    @DisplayName("summary totals only the requested month")
    void summarizesOneMonth() {
        add("2026-05-10", "100.00", "Swiggy");
        add("2026-06-10", "200.00", "Swiggy");
        add("2026-06-20", "300.00", "Uber");

        SummaryResponse june = dashboardService.summary(YearMonth.of(2026, 6));
        assertThat(june.month()).isEqualTo("2026-06");
        assertThat(june.totalAmount()).isEqualTo("500.00");
        assertThat(june.expenseCount()).isEqualTo(2);

        SummaryResponse allTime = dashboardService.summary(null);
        assertThat(allTime.month()).isNull();
        assertThat(allTime.totalAmount()).isEqualTo("600.00");
        assertThat(allTime.expenseCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("an empty month reports zeroes rather than nulls")
    void emptyMonthIsAllZeroes() {
        SummaryResponse summary = dashboardService.summary(YearMonth.of(2026, 3));

        assertThat(summary.totalAmount()).isEqualTo("0.00");
        assertThat(summary.expenseCount()).isZero();
        assertThat(summary.anomalyCount()).isZero();
        assertThat(summary.topCategoryName()).isNull();
    }

    @Test
    @DisplayName("months with no spend are zero-filled so every series shares one axis")
    void zeroFillsTheMonthAxis() {
        add("2026-04-10", "100.00", "Swiggy");
        add("2026-06-10", "250.00", "Swiggy");

        MonthlyCategoryTotalsResponse response =
                dashboardService.monthlyTotalsByCategory(YearMonth.of(2026, 3), YearMonth.of(2026, 6));

        assertThat(response.months()).containsExactly("2026-03", "2026-04", "2026-05", "2026-06");
        assertThat(response.series()).singleElement().satisfies(series -> {
            assertThat(series.categoryName()).isEqualTo("Food");
            assertThat(series.totals()).containsExactly("0.00", "100.00", "0.00", "250.00");
        });
    }

    @Test
    @DisplayName("every series has exactly one value per month on the axis")
    void everySeriesMatchesTheAxisLength() {
        add("2026-05-10", "100.00", "Swiggy");
        add("2026-06-10", "250.00", "Uber");
        add("2026-06-11", "400.00", "BigBasket");

        MonthlyCategoryTotalsResponse response =
                dashboardService.monthlyTotalsByCategory(YearMonth.of(2026, 4), YearMonth.of(2026, 6));

        assertThat(response.series()).isNotEmpty().allSatisfy(series ->
                assertThat(series.totals()).hasSameSizeAs(response.months()));
    }

    @Test
    @DisplayName("an inverted month range is rejected rather than silently returning nothing")
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> dashboardService.monthlyTotalsByCategory(YearMonth.of(2026, 6), YearMonth.of(2026, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("vendor spelling variants collapse into one bar")
    void groupsVendorSpellingVariants() {
        add("2026-06-01", "100.00", "SWIGGY");
        add("2026-06-02", "150.00", "Swiggy");
        add("2026-06-03", "200.00", "swiggy Pvt Ltd");
        add("2026-06-04", "900.00", "Uber");

        TopVendorsResponse response = dashboardService.topVendors(YearMonth.of(2026, 6), 5);

        assertThat(response.vendors()).hasSize(2);
        assertThat(response.vendors().get(0).vendorName()).isEqualTo("Uber");
        assertThat(response.vendors().get(1).totalAmount()).isEqualTo("450.00");
        assertThat(response.vendors().get(1).expenseCount()).isEqualTo(3);
        assertThat(response.vendors().get(1).topCategory()).isEqualTo("Food");
    }

    @Test
    @DisplayName("top vendors respects the limit and orders by spend")
    void limitsAndOrdersVendors() {
        add("2026-06-01", "100.00", "Swiggy");
        add("2026-06-02", "500.00", "Uber");
        add("2026-06-03", "300.00", "Zomato");
        add("2026-06-04", "900.00", "Amazon");
        add("2026-06-05", "50.00", "Netflix");
        add("2026-06-06", "700.00", "BigBasket");

        TopVendorsResponse response = dashboardService.topVendors(YearMonth.of(2026, 6), 3);

        assertThat(response.vendors()).extracting(TopVendorsResponse.VendorTotal::vendorName)
                .containsExactly("Amazon", "BigBasket", "Uber");
    }

    @Test
    @DisplayName("each anomaly carries the baseline that explains it")
    void anomalyItemsCarryTheirBaseline() {
        add("2026-06-01", "100.00", "Swiggy");
        add("2026-06-02", "100.00", "Swiggy");
        add("2026-06-03", "100.00", "Swiggy");
        add("2026-06-04", "1000.00", "Swiggy");

        var page = dashboardService.anomalies(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "amount")));

        assertThat(page.content()).singleElement().satisfies(item -> {
            assertThat(item.expense().amount()).isEqualTo("1000.00");
            assertThat(item.categoryAverage()).isEqualTo("100.00");
            assertThat(item.threshold()).isEqualTo("300.00");
            assertThat(item.timesAverage()).isEqualTo("10.00");
        });
    }

    @Test
    @DisplayName("the anomaly count on the summary matches the anomaly list")
    void summaryCountAgreesWithTheList() {
        add("2026-06-01", "100.00", "Swiggy");
        add("2026-06-02", "100.00", "Swiggy");
        add("2026-06-03", "100.00", "Swiggy");
        add("2026-06-04", "1000.00", "Swiggy");
        add("2026-06-05", "100.00", "Uber");
        add("2026-06-06", "100.00", "Uber");
        add("2026-06-07", "100.00", "Uber");
        add("2026-06-08", "5000.00", "Uber");

        SummaryResponse summary = dashboardService.summary(YearMonth.of(2026, 6));
        var page = dashboardService.anomalies(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "amount")));

        assertThat(summary.anomalyCount()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(summary.anomalyCount());
    }
}
