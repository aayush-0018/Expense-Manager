package com.iconcile.expense.web.dto;

import com.iconcile.expense.domain.VendorCategoryRule;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class VendorRuleDtos {

    private VendorRuleDtos() {
    }

    public record VendorRuleRequest(
            @NotBlank(message = "Pattern is required")
            @Size(max = 120, message = "Pattern must be at most 120 characters")
            String pattern,

            @NotNull(message = "Match type is required")
            @Pattern(regexp = "EXACT|CONTAINS", message = "Match type must be EXACT or CONTAINS")
            String matchType,

            @NotNull(message = "Category is required")
            Long categoryId,

            @Min(value = 1, message = "Priority must be at least 1")
            @Max(value = 1000, message = "Priority must be at most 1000")
            Integer priority,

            Boolean active
    ) {
    }

    public record VendorRuleResponse(
            Long id,
            String pattern,
            String matchType,
            CategoryResponse category,
            int priority,
            boolean active
    ) {
        public static VendorRuleResponse from(VendorCategoryRule rule) {
            return new VendorRuleResponse(
                    rule.getId(),
                    rule.getPattern(),
                    rule.getMatchType().name(),
                    CategoryResponse.from(rule.getCategory()),
                    rule.getPriority(),
                    rule.isActive());
        }
    }
}
