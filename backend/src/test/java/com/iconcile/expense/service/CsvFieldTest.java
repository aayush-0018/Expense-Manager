package com.iconcile.expense.service;

import com.iconcile.expense.service.csv.CsvField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CsvFieldTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @CsvSource({
            "date,              DATE",
            "Date,              DATE",
            "'Expense Date',    DATE",
            "expense_date,      DATE",
            "TRANSACTION DATE,  DATE",
            "amount,            AMOUNT",
            "Value,             AMOUNT",
            "vendor,            VENDOR",
            "Vendor Name,       VENDOR",
            "merchant,          VENDOR",
            "description,       DESCRIPTION",
            "Notes,             DESCRIPTION",
            "category,          CATEGORY",
    })
    @DisplayName("header spellings map onto logical columns regardless of case and punctuation")
    void resolvesAliases(String header, CsvField expected) {
        assertThat(CsvField.resolve(header)).isEqualTo(expected);
    }

    @Test
    @DisplayName("columns we do not understand are ignored, not fatal")
    void unknownHeadersResolveToNull() {
        assertThat(CsvField.resolve("balance")).isNull();
        assertThat(CsvField.resolve("")).isNull();
        assertThat(CsvField.resolve(null)).isNull();
    }

    @Test
    void dateAmountAndVendorAreRequired() {
        assertThat(CsvField.DATE.isRequired()).isTrue();
        assertThat(CsvField.AMOUNT.isRequired()).isTrue();
        assertThat(CsvField.VENDOR.isRequired()).isTrue();
        assertThat(CsvField.DESCRIPTION.isRequired()).isFalse();
        assertThat(CsvField.CATEGORY.isRequired()).isFalse();
    }

    @Test
    @DisplayName("the advertised template header parses back to the required columns")
    void templateHeaderIsSelfConsistent() {
        String[] headers = CsvField.templateHeader().split(",");
        assertThat(headers).extracting(CsvField::resolve)
                .containsExactly(CsvField.DATE, CsvField.AMOUNT, CsvField.VENDOR, CsvField.DESCRIPTION);
    }
}
