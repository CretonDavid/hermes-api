package com.hermes.api.mapper;

import com.hermes.api.dto.category.CategoryResponse;
import com.hermes.api.dto.transaction.TransactionResponse.CategorySummary;
import com.hermes.api.model.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category entity) {
        return new CategoryResponse(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getParent() == null ? null : entity.getParent().getId(),
                entity.getColor(),
                entity.getIcon()
        );
    }

    public CategorySummary toSummary(Category entity) {
        if (entity == null) {
            return null;
        }
        return new CategorySummary(entity.getId(), entity.getName(), entity.getColor(), entity.getIcon());
    }
}
