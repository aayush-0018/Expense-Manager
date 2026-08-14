package com.iconcile.expense.service;

import com.iconcile.expense.AbstractIntegrationTest;
import com.iconcile.expense.domain.Expense;
import com.iconcile.expense.web.dto.ImportResultResponse;
import com.iconcile.expense.web.error.CsvParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvImportIT extends AbstractIntegrationTest {

    @Autowired
    private CsvImportService csvImportService;

    private static MockMultipartFile csv(String content) {
        return csv("upload.csv", content);
    }

    private static MockMultipartFile csv(String filename, String content) {
        return new MockMultipartFile("file", filename, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a clean file imports every row and categorizes each one")
    void importsCleanFile() {
        ImportResultResponse result = csvImportService.importCsv(csv("""
                date,amount,vendor,description
                2026-06-01,450.00,Swiggy,Lunch
                2026-06-02,1200.00,Uber,Airport
                2026-06-03,2400.00,BigBasket,Groceries
                """));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.importedRows()).isEqualTo(3);
        assertThat(result.failedRows()).isZero();
        assertThat(expenseRepository.findAll())
                .extracting(expense -> expense.getCategory().getName())
                .containsExactlyInAnyOrder("Food", "Travel", "Groceries");
    }

    @Test
    @DisplayName("bad rows are reported and skipped; the good rows still land")
    void badRowsDoNotAbortTheFile() {
        ImportResultResponse result = csvImportService.importCsv(csv("""
                date,amount,vendor,description
                2026-06-01,450.00,Swiggy,Good
                2026-06-02,abc,Uber,Bad amount
                2026-06-03,-10.00,Ola,Negative
                2026-06-04,300.00,,Missing vendor
                2026-06-05,275.00,Zomato,Good
                """));

        assertThat(result.status()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(result.importedRows()).isEqualTo(2);
        assertThat(result.failedRows()).isEqualTo(3);
        assertThat(result.errors()).extracting(ImportResultResponse.RowError::field)
                .containsExactly("amount", "amount", "vendor");
        assertThat(expenseRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("error rows point at the line number in the uploaded file")
    void errorRowsUseFileLineNumbers() {
        ImportResultResponse result = csvImportService.importCsv(csv("""
                date,amount,vendor
                2026-06-01,450.00,Swiggy
                2026-06-02,oops,Uber
                """));

        // Header is line 1, the good row is line 2, the bad row is line 3.
        assertThat(result.errors()).singleElement()
                .satisfies(error -> assertThat(error.row()).isEqualTo(3));
    }

    @Test
    @DisplayName("header aliases and column order do not matter")
    void acceptsAliasedHeadersInAnyOrder() {
        ImportResultResponse result = csvImportService.importCsv(csv("""
                Notes,Merchant,Value,Transaction Date
                Lunch,Swiggy,450.00,01/06/2026
                Cab,Uber,220.00,02/06/2026
                """));

        assertThat(result.importedRows()).isEqualTo(2);
        assertThat(expenseRepository.findAll()).extracting(Expense::getDescription)
                .containsExactlyInAnyOrder("Lunch", "Cab");
    }

    @Test
    @DisplayName("one file cannot mix date conventions")
    void locksDateFormatToTheFirstRow() {
        ImportResultResponse result = csvImportService.importCsv(csv("""
                date,amount,vendor
                01/06/2026,450.00,Swiggy
                2026-06-02,220.00,Uber
                """));

        assertThat(result.importedRows()).isEqualTo(1);
        assertThat(result.errors()).singleElement()
                .satisfies(error -> assertThat(error.message()).contains("dd/MM/yyyy"));
    }

    @Test
    @DisplayName("a category column overrides the vendor rules for that row")
    void explicitCategoryOverridesRules() {
        csvImportService.importCsv(csv("""
                date,amount,vendor,category
                2026-06-01,450.00,Swiggy,Travel
                2026-06-02,450.00,Swiggy,
                """));

        assertThat(expenseRepository.findAll())
                .extracting(expense -> expense.getCategory().getName() + "/" + expense.getCategorizationSource())
                .containsExactlyInAnyOrder("Travel/MANUAL_OVERRIDE", "Food/RULE");
    }

    @Test
    @DisplayName("an unknown category name is a row error, not a silent fallback")
    void unknownCategoryIsARowError() {
        ImportResultResponse result = csvImportService.importCsv(csv("""
                date,amount,vendor,category
                2026-06-01,450.00,Swiggy,Yachting
                """));

        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errors()).singleElement()
                .satisfies(error -> assertThat(error.field()).isEqualTo("category"));
    }

    @Test
    @DisplayName("duplicates are reported but still imported")
    void duplicatesAreWarnedNotDropped() {
        csvImportService.importCsv(csv("""
                date,amount,vendor
                2026-06-01,450.00,Swiggy
                """));

        ImportResultResponse second = csvImportService.importCsv(csv("""
                date,amount,vendor
                2026-06-01,450.00,Swiggy
                2026-06-01,450.00,Swiggy
                """));

        assertThat(second.importedRows()).isEqualTo(2);
        assertThat(second.warnings()).hasSize(2);
        assertThat(expenseRepository.count()).as("nothing is silently discarded").isEqualTo(3);
    }

    @Test
    @DisplayName("anomalies are swept once after the whole file lands")
    void sweepsAnomaliesAfterImport() {
        csvImportService.importCsv(csv("""
                date,amount,vendor
                2026-06-01,100.00,Swiggy
                2026-06-02,100.00,Swiggy
                2026-06-03,100.00,Swiggy
                2026-06-04,1000.00,Swiggy
                """));

        assertThat(expenseRepository.findAll())
                .filteredOn(Expense::isAnomaly)
                .extracting(expense -> expense.getAmount().toPlainString())
                .containsExactly("1000.00");
    }

    @Test
    @DisplayName("a header-only file is a valid, empty import")
    void headerOnlyFileIsNotAnError() {
        ImportResultResponse result = csvImportService.importCsv(csv("date,amount,vendor\n"));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isZero();
        assertThat(result.importedRows()).isZero();
    }

    @Test
    @DisplayName("BOM, CRLF line endings and quoted commas are all handled")
    void handlesExcelFlavouredFiles() {
        ImportResultResponse result = csvImportService.importCsv(csv(
                "﻿date,amount,vendor,description\r\n"
                        + "2026-06-01,450.00,Swiggy,\"Lunch, drinks and dessert\"\r\n"));

        assertThat(result.importedRows()).isEqualTo(1);
        assertThat(expenseRepository.findAll()).singleElement()
                .satisfies(expense -> assertThat(expense.getDescription()).isEqualTo("Lunch, drinks and dessert"));
    }

    @Test
    @DisplayName("vendor spelling variants collapse to one grouping key")
    void normalizesVendorsForGrouping() {
        csvImportService.importCsv(csv("""
                date,amount,vendor
                2026-06-01,100.00,SWIGGY
                2026-06-02,100.00,Swiggy
                2026-06-03,100.00,swiggy Pvt Ltd
                """));

        assertThat(expenseRepository.findAll()).extracting(Expense::getVendorNormalized)
                .containsOnly("swiggy");
    }

    @Test
    @DisplayName("a missing required column fails the whole file with a usable message")
    void missingRequiredColumnFailsFast() {
        assertThatThrownBy(() -> csvImportService.importCsv(csv("""
                date,description
                2026-06-01,Lunch
                """)))
                .isInstanceOf(CsvParseException.class)
                .hasMessageContaining("Missing required column")
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("non-CSV uploads and empty files are refused")
    void refusesUnusableUploads() {
        assertThatThrownBy(() -> csvImportService.importCsv(csv("notes.txt", "date,amount,vendor\n")))
                .isInstanceOf(CsvParseException.class)
                .hasMessageContaining("Only .csv files");

        assertThatThrownBy(() -> csvImportService.importCsv(csv("")))
                .isInstanceOf(CsvParseException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("the batch record retains the counts and the error report")
    void recordsTheBatch() {
        ImportResultResponse result = csvImportService.importCsv(csv("august.csv", """
                date,amount,vendor
                2026-06-01,450.00,Swiggy
                2026-06-02,oops,Uber
                """));

        assertThat(csvImportBatchRepository.findById(result.batchId())).hasValueSatisfying(batch -> {
            assertThat(batch.getFilename()).isEqualTo("august.csv");
            assertThat(batch.getTotalRows()).isEqualTo(2);
            assertThat(batch.getImportedRows()).isEqualTo(1);
            assertThat(batch.getFailedRows()).isEqualTo(1);
            assertThat(batch.getStatus()).isEqualTo("COMPLETED_WITH_ERRORS");
            assertThat(batch.getErrorReport()).contains("amount");
        });

        assertThat(expenseRepository.findAll()).allSatisfy(expense ->
                assertThat(expense.getImportBatchId()).isEqualTo(result.batchId()));
    }

    @Test
    @DisplayName("blank lines in the middle of a file are skipped, not counted as failures")
    void ignoresBlankLines() {
        ImportResultResponse result = csvImportService.importCsv(csv("""
                date,amount,vendor
                2026-06-01,450.00,Swiggy

                2026-06-02,220.00,Uber
                """));

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.failedRows()).isZero();
        assertThat(List.of(result.importedRows())).containsExactly(2);
    }
}
