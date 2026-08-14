package com.iconcile.expense.repository;

import com.iconcile.expense.domain.Expense;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Predicate builders for the filtered expense list. Criteria API rather than a JPQL query
 * with a dozen {@code (:p IS NULL OR ...)} clauses: absent filters produce no predicate at
 * all, so PostgreSQL sees a query it can actually use the indexes for.
 */
public final class ExpenseSpecifications {

    private ExpenseSpecifications() {
    }

    public static Specification<Expense> withFilters(LocalDate from,
                                                     LocalDate to,
                                                     Long categoryId,
                                                     String vendor,
                                                     boolean anomalyOnly) {
        return (root, query, cb) -> {
            // The count query issued by Pageable must not carry a fetch join.
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("category");
            }

            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (vendor != null && !vendor.isBlank()) {
                String needle = "%" + vendor.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("vendorName")), needle),
                        cb.like(root.get("vendorNormalized"), needle)));
            }
            if (anomalyOnly) {
                predicates.add(cb.isTrue(root.get("anomaly")));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
