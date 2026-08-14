package com.iconcile.expense.web.dto;

import com.iconcile.expense.domain.Expense;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Wire representation of an expense.
 *
 * <p>{@code amount} is a decimal <em>string</em>: JavaScript numbers cannot represent every
 * 2-decimal money value exactly, and the client has no reason to do arithmetic on it.
 */
public record ExpenseResponse(
        Long id,
        LocalDate date,
        String amount,
        String vendorName,
        String description,
        CategoryResponse category,
        String categorizationSource,
        boolean isAnomaly,
        String anomalyReason,
        Long importBatchId,
        OffsetDateTime createdAt
) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getExpenseDate(),
                expense.getAmount().toPlainString(),
                expense.getVendorName(),
                expense.getDescription(),
                CategoryResponse.from(expense.getCategory()),
                expense.getCategorizationSource().name(),
                expense.isAnomaly(),
                expense.getAnomalyReason(),
                expense.getImportBatchId(),
                expense.getCreatedAt());
    }
}
