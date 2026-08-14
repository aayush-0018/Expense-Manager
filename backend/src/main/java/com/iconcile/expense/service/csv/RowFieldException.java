package com.iconcile.expense.service.csv;

/**
 * One cell of one row is unusable. Caught per row by the importer and turned into a report
 * entry - it never aborts the file.
 */
public class RowFieldException extends RuntimeException {

    private final String field;

    public RowFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
