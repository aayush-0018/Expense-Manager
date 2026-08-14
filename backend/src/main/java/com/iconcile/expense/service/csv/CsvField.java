package com.iconcile.expense.service.csv;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The logical columns the importer understands, and the header spellings that map onto them.
 *
 * <p>Exports from banks and spreadsheets rarely agree on header names, so headers are matched
 * on a squashed form (lowercase, alphanumerics only) against a list of aliases rather than
 * demanding one exact spelling.
 */
public enum CsvField {

    DATE(true, "date", "expensedate", "transactiondate", "txndate", "valuedate"),
    AMOUNT(true, "amount", "value", "price", "debit", "spend"),
    VENDOR(true, "vendor", "vendorname", "merchant", "merchantname", "payee"),
    DESCRIPTION(false, "description", "notes", "note", "remarks", "memo", "narration", "particulars"),
    CATEGORY(false, "category", "categoryname");

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");

    private final boolean required;
    private final Set<String> aliases;

    CsvField(boolean required, String... aliases) {
        this.required = required;
        this.aliases = Set.of(aliases);
    }

    public boolean isRequired() {
        return required;
    }

    /** Header spellings accepted for this column, for the error message and the UI's format hint. */
    public List<String> aliases() {
        return aliases.stream().sorted().toList();
    }

    /** Squash a raw header cell to its comparison form. */
    public static String squash(String header) {
        if (header == null) {
            return "";
        }
        return NON_ALNUM.matcher(header.toLowerCase()).replaceAll("");
    }

    /** @return the logical field this raw header maps to, or null if it is a column we ignore. */
    public static CsvField resolve(String rawHeader) {
        String squashed = squash(rawHeader);
        for (CsvField field : values()) {
            if (field.aliases.contains(squashed)) {
                return field;
            }
        }
        return null;
    }

    /** The canonical header line offered as a downloadable template. */
    public static String templateHeader() {
        return "date,amount,vendor,description";
    }

    /** Alias documentation, keyed by field name, for the API's format endpoint. */
    public static Map<String, List<String>> aliasCatalog() {
        return Map.of(
                DATE.name(), DATE.aliases(),
                AMOUNT.name(), AMOUNT.aliases(),
                VENDOR.name(), VENDOR.aliases(),
                DESCRIPTION.name(), DESCRIPTION.aliases(),
                CATEGORY.name(), CATEGORY.aliases());
    }
}
