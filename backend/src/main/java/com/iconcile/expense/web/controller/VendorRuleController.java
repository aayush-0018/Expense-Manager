package com.iconcile.expense.web.controller;

import com.iconcile.expense.domain.Category;
import com.iconcile.expense.domain.MatchType;
import com.iconcile.expense.domain.VendorCategoryRule;
import com.iconcile.expense.repository.CategoryRepository;
import com.iconcile.expense.repository.VendorCategoryRuleRepository;
import com.iconcile.expense.service.CategorizationService;
import com.iconcile.expense.util.VendorNameNormalizer;
import com.iconcile.expense.web.dto.CategoryResponse;
import com.iconcile.expense.web.dto.VendorRuleDtos.VendorRuleRequest;
import com.iconcile.expense.web.dto.VendorRuleDtos.VendorRuleResponse;
import com.iconcile.expense.web.error.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * CRUD over the vendor-to-category mapping, so the rules are data rather than code.
 *
 * <p>Editing a rule does not re-categorize expenses already recorded: an expense keeps the
 * category it was filed under, and manual overrides are never silently undone.
 */
@RestController
@RequestMapping("/api")
public class VendorRuleController {

    private final VendorCategoryRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final CategorizationService categorizationService;
    private final VendorNameNormalizer normalizer;

    public VendorRuleController(VendorCategoryRuleRepository ruleRepository,
                                CategoryRepository categoryRepository,
                                CategorizationService categorizationService,
                                VendorNameNormalizer normalizer) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.categorizationService = categorizationService;
        this.normalizer = normalizer;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return categoryRepository.findAllByOrderByNameAsc().stream().map(CategoryResponse::from).toList();
    }

    @GetMapping("/vendor-rules")
    public List<VendorRuleResponse> rules() {
        return ruleRepository.findAllOrdered().stream().map(VendorRuleResponse::from).toList();
    }

    @PostMapping("/vendor-rules")
    @Transactional
    public ResponseEntity<VendorRuleResponse> create(@Valid @RequestBody VendorRuleRequest request) {
        String pattern = normalizePattern(request.pattern());
        MatchType matchType = MatchType.valueOf(request.matchType());

        Optional<VendorCategoryRule> clash = ruleRepository.findByPatternAndMatchType(pattern, matchType);
        if (clash.isPresent()) {
            throw new IllegalArgumentException(
                    "A " + matchType + " rule for '" + pattern + "' already exists (id " + clash.get().getId() + ")");
        }

        VendorCategoryRule rule = new VendorCategoryRule(pattern, matchType, category(request.categoryId()),
                request.priority() == null ? 100 : request.priority());
        rule.setActive(request.active() == null || request.active());
        VendorCategoryRule saved = ruleRepository.save(rule);
        categorizationService.refresh();
        return ResponseEntity.status(HttpStatus.CREATED).body(VendorRuleResponse.from(saved));
    }

    @PutMapping("/vendor-rules/{id}")
    @Transactional
    public VendorRuleResponse update(@PathVariable Long id, @Valid @RequestBody VendorRuleRequest request) {
        VendorCategoryRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor rule", id));

        String pattern = normalizePattern(request.pattern());
        MatchType matchType = MatchType.valueOf(request.matchType());
        ruleRepository.findByPatternAndMatchType(pattern, matchType)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("A " + matchType + " rule for '" + pattern
                            + "' already exists (id " + other.getId() + ")");
                });

        rule.setPattern(pattern);
        rule.setMatchType(matchType);
        rule.setCategory(category(request.categoryId()));
        rule.setPriority(request.priority() == null ? rule.getPriority() : request.priority());
        rule.setActive(request.active() == null || request.active());
        VendorCategoryRule saved = ruleRepository.save(rule);
        categorizationService.refresh();
        return VendorRuleResponse.from(saved);
    }

    /** Deactivates rather than deletes, so an accidental removal is one toggle away from undone. */
    @DeleteMapping("/vendor-rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deactivate(@PathVariable Long id) {
        VendorCategoryRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor rule", id));
        rule.setActive(false);
        ruleRepository.save(rule);
        categorizationService.refresh();
    }

    /**
     * Patterns are stored in the same normalized form as vendor names, otherwise a rule typed
     * as "Uber Eats" would never match the normalized "uber eats".
     */
    private String normalizePattern(String rawPattern) {
        String normalized = normalizer.normalize(rawPattern);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Pattern '" + rawPattern + "' normalizes to an empty string");
        }
        return normalized;
    }

    private Category category(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }
}
