package com.iconcile.expense.service;

import com.iconcile.expense.service.csv.CsvValueParser;
import com.iconcile.expense.service.csv.RowFieldException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvValueParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 13);

    @Nested
    @DisplayName("dates")
    class Dates {

        @ParameterizedTest(name = "[{index}] {0}")
        @CsvSource({
                "2026-08-01, 2026-08-01",
                "01/08/2026, 2026-08-01",
                "01-08-2026, 2026-08-01",
                "2026/08/01, 2026-08-01",
        })
        void acceptsEachSupportedFormat(String raw, String expected) {
            assertThat(new CsvValueParser().parseDate(raw, TODAY)).isEqualTo(LocalDate.parse(expected));
        }

        @Test
        @DisplayName("the format is locked by the first row, so one file cannot mix conventions")
        void locksFormatAfterFirstRow() {
            CsvValueParser parser = new CsvValueParser();
            assertThat(parser.parseDate("01/08/2026", TODAY)).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(parser.lockedDateFormat()).isEqualTo("dd/MM/yyyy");

            assertThatThrownBy(() -> parser.parseDate("2026-08-02", TODAY))
                    .isInstanceOf(RowFieldException.class)
                    .hasMessageContaining("not a valid date in the format dd/MM/yyyy");
        }

        @Test
        @DisplayName("an impossible date is reported as invalid even once a format is locked")
        void rejectsImpossibleDatesUnderLockedFormat() {
            CsvValueParser parser = new CsvValueParser();
            parser.parseDate("15/06/2026", TODAY);
            assertThatThrownBy(() -> parser.parseDate("31/02/2026", TODAY))
                    .isInstanceOf(RowFieldException.class)
                    .hasMessageContaining("not a valid date");
        }

        @Test
        @DisplayName("an impossible date is rejected, not rolled forward")
        void rejectsImpossibleDates() {
            assertThatThrownBy(() -> new CsvValueParser().parseDate("31/02/2026", TODAY))
                    .isInstanceOf(RowFieldException.class)
                    .hasMessageContaining("Unparseable date");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "not-a-date", "13/13/2026", "2026-13-01"})
        void rejectsGarbage(String raw) {
            assertThatThrownBy(() -> new CsvValueParser().parseDate(raw, TODAY))
                    .isInstanceOf(RowFieldException.class);
        }

        @Test
        void rejectsFutureDates() {
            assertThatThrownBy(() -> new CsvValueParser().parseDate("2027-01-01", TODAY))
                    .isInstanceOf(RowFieldException.class)
                    .hasMessageContaining("in the future");
        }

        @Test
        void rejectsAncientDates() {
            assertThatThrownBy(() -> new CsvValueParser().parseDate("1999-12-31", TODAY))
                    .isInstanceOf(RowFieldException.class)
                    .hasMessageContaining("before 2000-01-01");
        }

        @Test
        @DisplayName("today itself is allowed")
        void acceptsToday() {
            assertThat(new CsvValueParser().parseDate("2026-08-13", TODAY)).isEqualTo(TODAY);
        }
    }

    @Nested
    @DisplayName("amounts")
    class Amounts {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource({
                "450,          450.00",
                "450.5,        450.50",
                "450.50,       450.50",
                "'1,234.56',   1234.56",
                "'Rs. 450',    450.00",
                "'INR 1,200',  1200.00",
                "'  99.99  ',  99.99",
        })
        void stripsCurrencyNoise(String raw, String expected) {
            assertThat(new CsvValueParser().parseAmount(raw)).isEqualByComparingTo(new BigDecimal(expected));
        }

        @Test
        @DisplayName("accounting-negative notation is refused rather than guessed at")
        void rejectsParenthesisedNegatives() {
            assertThatThrownBy(() -> new CsvValueParser().parseAmount("(123.45)"))
                    .isInstanceOf(RowFieldException.class)
                    .hasMessageContaining("looks negative");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "0.00", "-50", "abc", "", "   "})
        void rejectsNonPositiveAndGarbage(String raw) {
            assertThatThrownBy(() -> new CsvValueParser().parseAmount(raw))
                    .isInstanceOf(RowFieldException.class);
        }

        @Test
        @DisplayName("money is never silently rounded")
        void rejectsMoreThanTwoDecimals() {
            assertThatThrownBy(() -> new CsvValueParser().parseAmount("450.567"))
                    .isInstanceOf(RowFieldException.class)
                    .hasMessageContaining("more than 2 decimal places");
        }

        @Test
        void rejectsAbsurdAmounts() {
            assertThatThrownBy(() -> new CsvValueParser().parseAmount("99999999.00"))
                    .isInstanceOf(RowFieldException.class)
                    .hasMessageContaining("exceeds the maximum");
        }
    }

    @Nested
    @DisplayName("text fields")
    class TextFields {

        @Test
        void trimsVendorAndRequiresIt() {
            assertThat(new CsvValueParser().parseVendor("  Swiggy ")).isEqualTo("Swiggy");
            assertThatThrownBy(() -> new CsvValueParser().parseVendor("  "))
                    .isInstanceOf(RowFieldException.class)
                    .hasMessageContaining("required");
        }

        @Test
        void emptyDescriptionBecomesNull() {
            assertThat(new CsvValueParser().parseDescription("")).isNull();
            assertThat(new CsvValueParser().parseDescription("   ")).isNull();
            assertThat(new CsvValueParser().parseDescription(" lunch ")).isEqualTo("lunch");
        }

        @Test
        void rejectsOverlongText() {
            String longVendor = "x".repeat(121);
            assertThatThrownBy(() -> new CsvValueParser().parseVendor(longVendor))
                    .isInstanceOf(RowFieldException.class);
            assertThatThrownBy(() -> new CsvValueParser().parseDescription("y".repeat(501)))
                    .isInstanceOf(RowFieldException.class);
        }
    }
}
