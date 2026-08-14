package com.iconcile.expense.util;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a raw vendor string into a stable matching key.
 *
 * <p>Real statement lines are noisy - {@code "UPI/SWIGGY*ORDER 8823 BLR"},
 * {@code "Amazon Retail Pvt Ltd"}, {@code "POS 4412 UBER INDIA"}. Rule patterns are stored
 * already-normalized, so both sides of the comparison go through the same pipeline and
 * substring rules stay short and readable.
 *
 * <p>The same output is stored on {@code expense.vendor_normalized} and used to group the
 * top-vendors chart, so casing and punctuation variants of one merchant collapse into a
 * single bar.
 */
@Component
public class VendorNameNormalizer {

    /** Payment-rail noise that shows up at the start of card/UPI statement lines. */
    private static final Set<String> NOISE_TOKENS = Set.of(
            "upi", "pos", "neft", "imps", "rtgs", "ach", "atm", "vps", "ecom", "txn", "ref",
            "payment", "paytm", "gpay", "phonepe", "razorpay", "bharatpe", "billdesk", "payu");

    /**
     * Corporate suffixes that carry no signal for categorization.
     *
     * <p>Country words are deliberately absent: stripping "india" would turn "Air India" into
     * "air" and break the airline rule, which costs more than the noise it removes.
     */
    private static final Set<String> SUFFIX_TOKENS = Set.of(
            "pvt", "private", "ltd", "limited", "llp", "llc", "inc", "incorporated", "corp",
            "corporation", "co", "company", "technologies", "technology", "solutions", "services");

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern LONG_DIGITS = Pattern.compile("^\\d{4,}$");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /**
     * @return the normalized key, or the trimmed lowercase input when normalization would
     *         strip everything (a vendor named only with symbols still needs a group key).
     */
    public String normalize(String rawVendorName) {
        if (rawVendorName == null) {
            return "";
        }
        String ascii = DIACRITICS.matcher(Normalizer.normalize(rawVendorName, Normalizer.Form.NFD))
                .replaceAll("");
        String cleaned = NON_ALNUM.matcher(ascii.toLowerCase()).replaceAll(" ").trim();
        if (cleaned.isEmpty()) {
            return rawVendorName.trim().toLowerCase();
        }

        List<String> tokens = new ArrayList<>();
        for (String token : cleaned.split(" ")) {
            if (token.isEmpty() || LONG_DIGITS.matcher(token).matches()) {
                continue;
            }
            tokens.add(token);
        }

        // Leading rail noise only - "paytm" mid-string could be the merchant itself.
        int start = 0;
        while (start < tokens.size() && NOISE_TOKENS.contains(tokens.get(start))) {
            start++;
        }
        // Trailing corporate boilerplate.
        int end = tokens.size();
        while (end > start && SUFFIX_TOKENS.contains(tokens.get(end - 1))) {
            end--;
        }

        String result = String.join(" ", tokens.subList(start, end));
        if (result.isBlank()) {
            // Everything was noise; keep the cleaned form rather than an empty key.
            return cleaned;
        }
        return result;
    }
}
