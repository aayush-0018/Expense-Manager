package com.iconcile.expense.web.dto;

import com.iconcile.expense.domain.Category;

public record CategoryResponse(Long id, String name, String colorHex, boolean isDefault) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getColorHex(),
                category.isDefault());
    }
}
