package com.iconcile.expense.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "csv")
public class CsvImportProperties {

    /** Hard cap on data rows per upload. Guards against a runaway file holding a long transaction. */
    private int maxRows = 10_000;

    /** Rows flushed to the database at a time during import. */
    private int batchSize = 500;

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
