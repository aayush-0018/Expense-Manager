package com.iconcile.expense.web.controller;

import com.iconcile.expense.service.DashboardService;
import com.iconcile.expense.web.dto.DashboardDtos.AnomalyItem;
import com.iconcile.expense.web.dto.DashboardDtos.MonthlyCategoryTotalsResponse;
import com.iconcile.expense.web.dto.DashboardDtos.SummaryResponse;
import com.iconcile.expense.web.dto.DashboardDtos.TopVendorsResponse;
import com.iconcile.expense.web.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final int MAX_MONTH_SPAN = 60;

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** @param month {@code yyyy-MM}; omit for all time. */
    @GetMapping("/summary")
    public SummaryResponse summary(@RequestParam(required = false) String month) {
        return dashboardService.summary(parseMonth(month, "month"));
    }

    /**
     * @param from inclusive {@code yyyy-MM}; defaults to 5 months before {@code to}
     * @param to   inclusive {@code yyyy-MM}; defaults to the current month
     */
    @GetMapping("/monthly-by-category")
    public MonthlyCategoryTotalsResponse monthlyByCategory(@RequestParam(required = false) String from,
                                                           @RequestParam(required = false) String to) {
        YearMonth end = parseMonth(to, "to");
        if (end == null) {
            end = YearMonth.now();
        }
        YearMonth start = parseMonth(from, "from");
        if (start == null) {
            start = end.minusMonths(5);
        }
        // An unbounded range would generate an axis with thousands of entries.
        if (start.plusMonths(MAX_MONTH_SPAN).isBefore(end)) {
            throw new IllegalArgumentException("Range must not exceed " + MAX_MONTH_SPAN + " months");
        }
        return dashboardService.monthlyTotalsByCategory(start, end);
    }

    @GetMapping("/top-vendors")
    public TopVendorsResponse topVendors(@RequestParam(required = false) String month,
                                         @RequestParam(defaultValue = "5") int limit) {
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit must be between 1 and 50");
        }
        return dashboardService.topVendors(parseMonth(month, "month"), limit);
    }

    @GetMapping("/anomalies")
    public PageResponse<AnomalyItem> anomalies(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        int safeSize = size < 1 ? 20 : Math.min(size, 200);
        return dashboardService.anomalies(PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "amount").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    private static YearMonth parseMonth(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("'" + parameterName + "' must be in yyyy-MM format, got '" + value + "'");
        }
    }
}
