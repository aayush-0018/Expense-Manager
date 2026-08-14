package com.iconcile.expense.service;

import com.iconcile.expense.domain.CategorizationSource;
import com.iconcile.expense.domain.Category;
import com.iconcile.expense.domain.MatchType;
import com.iconcile.expense.domain.VendorCategoryRule;
import com.iconcile.expense.repository.CategoryRepository;
import com.iconcile.expense.repository.VendorCategoryRuleRepository;
import com.iconcile.expense.service.CategorizationService.CategorizationResult;
import com.iconcile.expense.util.VendorNameNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategorizationServiceTest {

    private static final long FOOD = 1L;
    private static final long TRAVEL = 2L;
    private static final long GROCERIES = 3L;
    private static final long UNCATEGORIZED = 99L;

    private VendorCategoryRuleRepository ruleRepository;
    private CategoryRepository categoryRepository;
    private CategorizationService service;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(VendorCategoryRuleRepository.class);
        categoryRepository = mock(CategoryRepository.class);

        when(categoryRepository.findByIsDefaultTrue())
                .thenReturn(Optional.of(category(UNCATEGORIZED, "Uncategorized", true)));
        when(ruleRepository.findAllActiveOrdered()).thenReturn(rules());

        service = new CategorizationService(ruleRepository, categoryRepository, new VendorNameNormalizer());
    }

    @Test
    @DisplayName("a plain vendor name matches its CONTAINS rule")
    void matchesContainsRule() {
        CategorizationResult result = service.categorize("Swiggy");
        assertThat(result.categoryId()).isEqualTo(FOOD);
        assertThat(result.source()).isEqualTo(CategorizationSource.RULE);
    }

    @Test
    @DisplayName("matching survives the noise a real statement line carries")
    void matchesThroughStatementNoise() {
        assertThat(service.categorize("UPI/SWIGGY*ORDER 8823 BLR").categoryId()).isEqualTo(FOOD);
        assertThat(service.categorize("POS 4412 UBER INDIA SYSTEMS PVT LTD").categoryId()).isEqualTo(TRAVEL);
    }

    @Test
    @DisplayName("the more specific pattern wins: 'uber eats' is Food, not Travel")
    void specificPatternBeatsGenericPrefix() {
        assertThat(service.categorize("Uber Eats").categoryId()).isEqualTo(FOOD);
        assertThat(service.categorize("Uber").categoryId()).isEqualTo(TRAVEL);
        assertThat(service.categorize("UBER   EATS BANGALORE").categoryId()).isEqualTo(FOOD);
    }

    @Test
    @DisplayName("explicit priority beats pattern length: 'swiggy instamart' is Groceries")
    void priorityBeatsLength() {
        assertThat(service.categorize("Swiggy Instamart").categoryId()).isEqualTo(GROCERIES);
        assertThat(service.categorize("Swiggy").categoryId()).isEqualTo(FOOD);
    }

    @Test
    @DisplayName("an EXACT rule only fires on the whole normalized name")
    void exactRuleDoesNotMatchSubstrings() {
        assertThat(service.categorize("Ola").categoryId()).isEqualTo(TRAVEL);
        // 'coca cola' contains 'ola', but the rule is EXACT, so it must not be Travel.
        assertThat(service.categorize("Coca Cola").categoryId()).isEqualTo(UNCATEGORIZED);
    }

    @Test
    @DisplayName("EXACT is checked before CONTAINS")
    void exactWinsOverContains() {
        // 'zepto' has both an EXACT rule (Groceries) and a CONTAINS rule (Food) in this fixture.
        assertThat(service.categorize("Zepto").categoryId()).isEqualTo(GROCERIES);
        assertThat(service.categorize("Zepto Express").categoryId()).isEqualTo(FOOD);
    }

    @Test
    @DisplayName("an unknown vendor falls back to the default category")
    void unknownVendorFallsBackToDefault() {
        CategorizationResult result = service.categorize("Some Corner Shop");
        assertThat(result.categoryId()).isEqualTo(UNCATEGORIZED);
        assertThat(result.source()).isEqualTo(CategorizationSource.DEFAULT);
    }

    @Test
    @DisplayName("blank and null vendor names fall back rather than throw")
    void blankVendorFallsBack() {
        assertThat(service.categorize(null).categoryId()).isEqualTo(UNCATEGORIZED);
        assertThat(service.categorize("   ").categoryId()).isEqualTo(UNCATEGORIZED);
    }

    @Test
    @DisplayName("rules are loaded once and reused; refresh() forces a reload")
    void cachesRulesUntilRefreshed() {
        service.categorize("Swiggy");
        service.categorize("Uber");
        service.categorize("Netflix");
        verify(ruleRepository, times(1)).findAllActiveOrdered();

        service.refresh();
        service.categorize("Swiggy");
        verify(ruleRepository, times(2)).findAllActiveOrdered();
    }

    @Test
    @DisplayName("a missing default category is a startup-level misconfiguration, not a silent null")
    void requiresADefaultCategory() {
        when(categoryRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
        service.refresh();
        assertThatThrownBy(() -> service.categorize("Swiggy"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No default category");
    }

    private static List<VendorCategoryRule> rules() {
        List<VendorCategoryRule> rules = new ArrayList<>();
        rules.add(rule("uber eats", MatchType.CONTAINS, FOOD, "Food", 10));
        rules.add(rule("swiggy instamart", MatchType.CONTAINS, GROCERIES, "Groceries", 5));
        rules.add(rule("swiggy", MatchType.CONTAINS, FOOD, "Food", 100));
        rules.add(rule("uber", MatchType.CONTAINS, TRAVEL, "Travel", 100));
        rules.add(rule("ola", MatchType.EXACT, TRAVEL, "Travel", 100));
        rules.add(rule("zepto", MatchType.EXACT, GROCERIES, "Groceries", 100));
        rules.add(rule("zepto", MatchType.CONTAINS, FOOD, "Food", 100));
        rules.add(rule("netflix", MatchType.CONTAINS, FOOD, "Food", 100));
        return rules;
    }

    private static VendorCategoryRule rule(String pattern, MatchType type, long categoryId, String name, int priority) {
        return new VendorCategoryRule(pattern, type, category(categoryId, name, false), priority);
    }

    private static Category category(long id, String name, boolean isDefault) {
        Category category = new Category(name, "#000000", isDefault);
        setId(category, id);
        return category;
    }

    /** Ids are database-generated; tests set them directly rather than through a setter. */
    private static void setId(Category category, long id) {
        try {
            Field field = Category.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(category, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
