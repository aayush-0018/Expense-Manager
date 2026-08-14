package com.iconcile.expense.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload for creating or updating an expense.
 *
 * <p>{@code categoryId} is optional: leave it null to let the rule engine decide, or set it to
 * override the rules for this one expense.
 */
public record ExpenseRequest(

        @NotNull(message = "Date is required")
        @PastOrPresent(message = "Date cannot be in the future")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than 0")
        @DecimalMax(value = "10000000.00", message = "Amount must not exceed 10,000,000")
        @Digits(integer = 12, fraction = 2, message = "Amount may have at most 2 decimal places")
        BigDecimal amount,

        @NotBlank(message = "Vendor name is required")
        @Size(max = 120, message = "Vendor name must be at most 120 characters")
        String vendorName,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        Long categoryId
) {

    private static final LocalDate EARLIEST_ALLOWED = LocalDate.of(2000, 1, 1);

    /**
     * Guards against year typos ({@code 0226} for {@code 2026}) that would otherwise sit in the
     * data forever, dragging every date range and month axis back with them. Mirrors the same
     * floor the CSV importer applies, so both entry paths accept exactly the same dates.
     */
    @AssertTrue(message = "Date must be on or after 2000-01-01")
    @JsonIgnore
    public boolean isDateWithinSupportedRange() {
        return date == null || !date.isBefore(EARLIEST_ALLOWED);
    }
}
