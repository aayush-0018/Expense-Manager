package com.iconcile.expense.service;

import com.iconcile.expense.domain.CategorizationSource;
import com.iconcile.expense.domain.Category;
import com.iconcile.expense.domain.MatchType;
import com.iconcile.expense.domain.VendorCategoryRule;
import com.iconcile.expense.repository.CategoryRepository;
import com.iconcile.expense.repository.VendorCategoryRuleRepository;
import com.iconcile.expense.util.VendorNameNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule-based vendor to category mapping.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>EXACT match on the normalized vendor name</li>
 *   <li>CONTAINS match, most specific first - priority ascending, then longest pattern</li>
 *   <li>the default category ({@code Uncategorized})</li>
 * </ol>
 *
 * <p>Rules are held in memory. CSV import calls this once per row, and a database round trip
 * per row would dominate import time; the rule set is a few hundred rows at most, so matching
 * is pure in-memory string work. {@link #refresh()} is called whenever a rule is written.
 */
@Service
public class CategorizationService {

    private static final Logger log = LoggerFactory.getLogger(CategorizationService.class);

    private final VendorCategoryRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final VendorNameNormalizer normalizer;

    private volatile Snapshot snapshot;

    public CategorizationService(VendorCategoryRuleRepository ruleRepository,
                                 CategoryRepository categoryRepository,
                                 VendorNameNormalizer normalizer) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.normalizer = normalizer;
    }

    /** Outcome of categorizing one vendor string. */
    public record CategorizationResult(Long categoryId, CategorizationSource source) {
    }

    /** Categorize a raw vendor name, normalizing it first. */
    public CategorizationResult categorize(String rawVendorName) {
        return categorizeNormalized(normalizer.normalize(rawVendorName));
    }

    /** Categorize an already-normalized vendor name (CSV import normalizes once, up front). */
    public CategorizationResult categorizeNormalized(String normalizedVendorName) {
        Snapshot current = ensureLoaded();
        if (normalizedVendorName != null && !normalizedVendorName.isBlank()) {
            Long exact = current.exactPatterns().get(normalizedVendorName);
            if (exact != null) {
                return new CategorizationResult(exact, CategorizationSource.RULE);
            }
            for (ContainsRule rule : current.containsRules()) {
                if (normalizedVendorName.contains(rule.pattern())) {
                    return new CategorizationResult(rule.categoryId(), CategorizationSource.RULE);
                }
            }
        }
        return new CategorizationResult(current.defaultCategoryId(), CategorizationSource.DEFAULT);
    }

    /** The fallback category id, used when no rule matches. */
    public Long defaultCategoryId() {
        return ensureLoaded().defaultCategoryId();
    }

    /** Drops the cached rule set; the next categorization reloads it. */
    public void refresh() {
        this.snapshot = null;
    }

    private Snapshot ensureLoaded() {
        Snapshot current = snapshot;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (snapshot == null) {
                snapshot = load();
            }
            return snapshot;
        }
    }

    private Snapshot load() {
        Category fallback = categoryRepository.findByIsDefaultTrue()
                .orElseThrow(() -> new IllegalStateException(
                        "No default category is configured; expected exactly one category with is_default = true"));

        List<VendorCategoryRule> rules = ruleRepository.findAllActiveOrdered();

        Map<String, Long> exact = new HashMap<>();
        List<ContainsRule> contains = new ArrayList<>();
        for (VendorCategoryRule rule : rules) {
            if (rule.getMatchType() == MatchType.EXACT) {
                exact.putIfAbsent(rule.getPattern(), rule.getCategory().getId());
            } else {
                contains.add(new ContainsRule(rule.getPattern(), rule.getCategory().getId(), rule.getPriority()));
            }
        }
        // Most specific first: explicit priority wins, then the longer pattern
        // ("uber eats" must beat "uber", "swiggy instamart" must beat "swiggy").
        contains.sort(Comparator
                .comparingInt(ContainsRule::priority)
                .thenComparing((ContainsRule r) -> r.pattern().length(), Comparator.reverseOrder())
                .thenComparing(ContainsRule::pattern));

        log.debug("Loaded categorization rules: {} exact, {} contains, default category id {}",
                exact.size(), contains.size(), fallback.getId());
        return new Snapshot(exact, List.copyOf(contains), fallback.getId());
    }

    private record ContainsRule(String pattern, Long categoryId, int priority) {
    }

    private record Snapshot(Map<String, Long> exactPatterns, List<ContainsRule> containsRules, Long defaultCategoryId) {
    }
}
