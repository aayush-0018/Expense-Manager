package com.iconcile.expense.service;

import com.iconcile.expense.domain.CategorizationSource;
import com.iconcile.expense.domain.Category;
import com.iconcile.expense.domain.Expense;
import com.iconcile.expense.repository.CategoryRepository;
import com.iconcile.expense.repository.ExpenseRepository;
import com.iconcile.expense.repository.ExpenseSpecifications;
import com.iconcile.expense.service.CategorizationService.CategorizationResult;
import com.iconcile.expense.util.VendorNameNormalizer;
import com.iconcile.expense.web.dto.ExpenseRequest;
import com.iconcile.expense.web.error.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final CategorizationService categorizationService;
    private final AnomalyService anomalyService;
    private final VendorNameNormalizer normalizer;

    public ExpenseService(ExpenseRepository expenseRepository,
                          CategoryRepository categoryRepository,
                          CategorizationService categorizationService,
                          AnomalyService anomalyService,
                          VendorNameNormalizer normalizer) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.categorizationService = categorizationService;
        this.anomalyService = anomalyService;
        this.normalizer = normalizer;
    }

    @Transactional(readOnly = true)
    public Page<Expense> search(LocalDate from,
                                LocalDate to,
                                Long categoryId,
                                String vendor,
                                boolean anomalyOnly,
                                Pageable pageable) {
        return expenseRepository.findAll(
                ExpenseSpecifications.withFilters(from, to, categoryId, vendor, anomalyOnly), pageable);
    }

    @Transactional(readOnly = true)
    public Expense get(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", id));
    }

    /**
     * Creates an expense, categorizes it, and re-sweeps its category for anomalies - all in one
     * transaction, so a caller never observes an expense whose category flags are stale.
     */
    @Transactional
    public Expense create(ExpenseRequest request) {
        String normalized = normalizer.normalize(request.vendorName());
        Resolved resolved = resolveCategory(request.categoryId(), normalized);

        Expense expense = new Expense(
                request.date(),
                request.amount(),
                request.vendorName().trim(),
                normalized,
                trimToNull(request.description()),
                resolved.category(),
                resolved.source());
        Expense saved = expenseRepository.save(expense);

        anomalyService.reevaluateCategory(resolved.category().getId());
        return refresh(saved.getId());
    }

    /**
     * Updates an expense. Both the old and the new category are re-swept when the category or
     * the amount moves, because either can change who the outliers are on both sides.
     */
    @Transactional
    public Expense update(Long id, ExpenseRequest request) {
        Expense expense = get(id);
        Long previousCategoryId = expense.getCategory().getId();

        String normalized = normalizer.normalize(request.vendorName());
        Resolved resolved = resolveCategory(request.categoryId(), normalized);

        expense.setExpenseDate(request.date());
        expense.setAmount(request.amount());
        expense.setVendorName(request.vendorName().trim());
        expense.setVendorNormalized(normalized);
        expense.setDescription(trimToNull(request.description()));
        expense.setCategory(resolved.category());
        expense.setCategorizationSource(resolved.source());
        expenseRepository.saveAndFlush(expense);

        Set<Long> affected = new LinkedHashSet<>();
        affected.add(previousCategoryId);
        affected.add(resolved.category().getId());
        anomalyService.reevaluateCategories(affected);

        return refresh(id);
    }

    @Transactional
    public void delete(Long id) {
        Expense expense = get(id);
        Long categoryId = expense.getCategory().getId();
        expenseRepository.delete(expense);
        expenseRepository.flush();
        anomalyService.reevaluateCategory(categoryId);
    }

    /**
     * Resolves the category for an expense: an explicit id always wins over the rule engine,
     * which is what makes a manual correction stick.
     */
    private Resolved resolveCategory(Long explicitCategoryId, String normalizedVendor) {
        if (explicitCategoryId != null) {
            Category category = categoryRepository.findById(explicitCategoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category", explicitCategoryId));
            return new Resolved(category, CategorizationSource.MANUAL_OVERRIDE);
        }
        CategorizationResult result = categorizationService.categorizeNormalized(normalizedVendor);
        Category category = categoryRepository.findById(result.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", result.categoryId()));
        return new Resolved(category, result.source());
    }

    /** Re-reads after the anomaly sweep, which updates rows behind the persistence context. */
    private Expense refresh(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", id));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record Resolved(Category category, CategorizationSource source) {
    }
}
