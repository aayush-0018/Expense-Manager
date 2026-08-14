package com.iconcile.expense.web.error;

/**
 * The uploaded file could not be read as a CSV at all - unreadable, empty, or missing the
 * columns the importer needs. Individual bad <em>rows</em> do not throw; they are collected
 * into the import report so the rest of the file still lands.
 */
public class CsvParseException extends RuntimeException {

    public CsvParseException(String message) {
        super(message);
    }

    public CsvParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
