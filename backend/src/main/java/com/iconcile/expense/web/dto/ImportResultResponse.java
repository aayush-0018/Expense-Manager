package com.iconcile.expense.web.dto;

import java.util.List;

/**
 * Outcome of a CSV import.
 *
 * <p>{@code row} on errors and warnings is the <em>line number in the uploaded file</em>
 * (the header is line 1), so the number can be typed straight into a spreadsheet's go-to-line.
 */
public record ImportResultResponse(
        Long batchId,
        String filename,
        int totalRows,
        int importedRows,
        int failedRows,
        String status,
        List<RowError> errors,
        List<RowWarning> warnings
) {

    public record RowError(long row, String field, String message) {
    }

    public record RowWarning(long row, String message) {
    }
}
