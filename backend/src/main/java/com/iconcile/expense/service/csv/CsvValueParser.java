package com.iconcile.expense.service.csv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses the scalar cells of a CSV row.
 *
 * <p>Stateful by design: the date format is <em>locked</em> to whichever supported pattern
 * parses the first date in the file, and every later row must use that same pattern. Without
 * the lock, a file containing {@code 01/02/2026} and {@code 13/02/2026} would silently be read
 * as two different date conventions - the first as either January or February depending on
 * pattern order, the second forcing day-first. Locking turns that ambiguity into a visible row
 * error instead of quietly wrong data.
 *
 * <p>One instance per import; not thread safe.
 */
public class CsvValueParser {

    /** Supported date patterns, in the order they are tried on the first date seen. */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            formatter("uuuu-MM-dd"),
            formatter("dd/MM/uuuu"),
            formatter("dd-MM-uuuu"),
            formatter("uuuu/MM/dd"));

    private static final List<String> DATE_PATTERN_LABELS =
            List.of("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "yyyy/MM/dd");

    private static final LocalDate EARLIEST_ALLOWED = LocalDate.of(2000, 1, 1);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000.00");

    /** Currency symbols, thousands separators and stray whitespace around an amount. */
    private static final Pattern AMOUNT_NOISE = Pattern.compile("[\\s, ₹$€£]|(?i)\\b(?:rs|inr|usd|eur|gbp)\\b\\.?");
    private static final Pattern PARENTHESISED = Pattern.compile("^\\(.*\\)$");

    private DateTimeFormatter lockedFormat;
    private String lockedFormatLabel;

    /** Which date pattern this file turned out to use; null until the first date parses. */
    public String lockedDateFormat() {
        return lockedFormatLabel;
    }

    public LocalDate parseDate(String raw, LocalDate today) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new RowFieldException("date", "Date is required");
        }

        LocalDate parsed;
        if (lockedFormat != null) {
            parsed = tryParse(lockedFormat, value);
            if (parsed == null) {
                // Covers both causes: a different format, and a shape-valid but impossible
                // date like 31/02 - which reads as nonsense if we only mention the format.
                throw new RowFieldException("date", "Date '" + value + "' is not a valid date in the format "
                        + lockedFormatLabel + " locked by the first row of this file");
            }
        } else {
            parsed = null;
            for (int i = 0; i < DATE_FORMATS.size() && parsed == null; i++) {
                parsed = tryParse(DATE_FORMATS.get(i), value);
                if (parsed != null) {
                    lockedFormat = DATE_FORMATS.get(i);
                    lockedFormatLabel = DATE_PATTERN_LABELS.get(i);
                }
            }
            if (parsed == null) {
                throw new RowFieldException("date", "Unparseable date '" + value
                        + "'; expected one of " + String.join(", ", DATE_PATTERN_LABELS));
            }
        }

        if (parsed.isAfter(today)) {
            throw new RowFieldException("date", "Date " + parsed + " is in the future");
        }
        if (parsed.isBefore(EARLIEST_ALLOWED)) {
            throw new RowFieldException("date", "Date " + parsed + " is before " + EARLIEST_ALLOWED);
        }
        return parsed;
    }

    public BigDecimal parseAmount(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new RowFieldException("amount", "Amount is required");
        }
        // "(123.45)" is accounting notation for a negative. Rejected rather than guessed at:
        // an expense manager has no meaning for a negative expense.
        if (PARENTHESISED.matcher(value).matches()) {
            throw new RowFieldException("amount", "Amount '" + value + "' looks negative; expenses must be positive");
        }

        String cleaned = AMOUNT_NOISE.matcher(value).replaceAll("");
        if (cleaned.isEmpty()) {
            throw new RowFieldException("amount", "Not a valid number: '" + value + "'");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            throw new RowFieldException("amount", "Not a valid number: '" + value + "'");
        }

        if (amount.signum() <= 0) {
            throw new RowFieldException("amount", "Amount must be greater than 0, got '" + value + "'");
        }
        if (amount.scale() > 2) {
            // The column is NUMERIC(14,2); rounding money without telling anyone is worse
            // than refusing the row.
            throw new RowFieldException("amount", "Amount '" + value + "' has more than 2 decimal places");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new RowFieldException("amount", "Amount '" + value + "' exceeds the maximum of " + MAX_AMOUNT);
        }
        return amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    public String parseVendor(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new RowFieldException("vendor", "Vendor name is required");
        }
        if (value.length() > 120) {
            throw new RowFieldException("vendor", "Vendor name exceeds 120 characters");
        }
        return value;
    }

    public String parseDescription(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > 500) {
            throw new RowFieldException("description", "Description exceeds 500 characters");
        }
        return value;
    }

    private static LocalDate tryParse(DateTimeFormatter formatter, String value) {
        try {
            return LocalDate.parse(value, formatter);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static DateTimeFormatter formatter(String pattern) {
        // STRICT + uuuu rejects impossible dates like 31/02/2026 instead of shifting them.
        return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
    }
}
