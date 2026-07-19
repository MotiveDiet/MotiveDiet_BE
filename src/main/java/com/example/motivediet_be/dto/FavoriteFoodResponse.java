package com.example.motivediet_be.dto;

public record FavoriteFoodResponse(
        Long favoriteFoodId,
        Long foodCategoryId,
        String name,
        String emoji,
        long weeklyCount,
        int slotOrder) {
}
