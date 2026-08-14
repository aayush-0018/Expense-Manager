package com.iconcile.expense.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class VendorNameNormalizerTest {

    private final VendorNameNormalizer normalizer = new VendorNameNormalizer();

    @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
    @CsvSource({
            "Swiggy,                        swiggy",
            "SWIGGY,                        swiggy",
            "'  Swiggy  ',                  swiggy",
            "Swiggy*Order,                  swiggy order",
            "'UPI/SWIGGY*ORDER 8823 BLR',   swiggy order blr",
            "'Amazon Retail Pvt Ltd',       amazon retail",
            "'UBER INDIA SYSTEMS PVT LTD',  uber india systems",
            "'POS 4412 Starbucks',          starbucks",
            "'Cafe   Coffee    Day',        cafe coffee day",
            "'Zomato-Ltd.',                 zomato",
            "'NETFLIX.COM',                 netflix com",
            "'1mg Technologies',            1mg",
    })
    @DisplayName("strips rail noise, corporate suffixes, punctuation and long transaction ids")
    void normalizesRealWorldVendorStrings(String raw, String expected) {
        assertThat(normalizer.normalize(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("casing and punctuation variants collapse to one grouping key")
    void variantsShareOneKey() {
        String canonical = normalizer.normalize("Swiggy");
        assertThat(normalizer.normalize("SWIGGY ")).isEqualTo(canonical);
        assertThat(normalizer.normalize("swiggy.")).isEqualTo(canonical);
        assertThat(normalizer.normalize("Swiggy Pvt Ltd")).isEqualTo(canonical);
    }

    @Test
    @DisplayName("diacritics are folded so Cafe and Café group together")
    void foldsDiacritics() {
        assertThat(normalizer.normalize("Café Noir")).isEqualTo("cafe noir");
    }

    @Test
    @DisplayName("null and blank input never blow up")
    void handlesEmptyInput() {
        assertThat(normalizer.normalize(null)).isEmpty();
        assertThat(normalizer.normalize("   ")).isEmpty();
    }

    @Test
    @DisplayName("a name made only of noise keeps a usable key rather than becoming empty")
    void keepsAKeyWhenEverythingLooksLikeNoise() {
        assertThat(normalizer.normalize("Pvt Ltd")).isEqualTo("pvt ltd");
        assertThat(normalizer.normalize("###")).isEqualTo("###");
    }

    @Test
    @DisplayName("country words are not treated as boilerplate; 'Air India' must survive intact")
    void doesNotStripCountryWords() {
        assertThat(normalizer.normalize("Air India Ltd")).isEqualTo("air india");
        assertThat(normalizer.normalize("AIR INDIA")).isEqualTo("air india");
    }

    @Test
    @DisplayName("short digit groups survive, long transaction ids do not")
    void stripsOnlyLongDigitRuns() {
        assertThat(normalizer.normalize("Store 365")).isEqualTo("store 365");
        assertThat(normalizer.normalize("Store 1234567")).isEqualTo("store");
    }
}
