package com.iconcile.expense.repository;

import com.iconcile.expense.domain.MatchType;
import com.iconcile.expense.domain.VendorCategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VendorCategoryRuleRepository extends JpaRepository<VendorCategoryRule, Long> {

    @Query("""
           SELECT r FROM VendorCategoryRule r
           JOIN FETCH r.category
           WHERE r.active = true
           ORDER BY r.priority ASC, LENGTH(r.pattern) DESC, r.id ASC
           """)
    List<VendorCategoryRule> findAllActiveOrdered();

    @Query("""
           SELECT r FROM VendorCategoryRule r
           JOIN FETCH r.category
           ORDER BY r.active DESC, r.priority ASC, r.pattern ASC
           """)
    List<VendorCategoryRule> findAllOrdered();

    Optional<VendorCategoryRule> findByPatternAndMatchType(String pattern, MatchType matchType);
}
