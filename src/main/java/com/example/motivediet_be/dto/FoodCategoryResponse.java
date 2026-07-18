package com.example.motivediet_be.dto;

import com.example.motivediet_be.domain.FoodCategory;

public record FoodCategoryResponse(Long foodCategoryId, String name, String emoji) {

    public static FoodCategoryResponse from(FoodCategory c) {
        return new FoodCategoryResponse(c.getId(), c.getName(), c.getEmoji());
    }
}
