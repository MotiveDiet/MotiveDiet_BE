package com.example.motivediet_be.controller;

import com.example.motivediet_be.dto.FoodCategoryResponse;
import com.example.motivediet_be.service.FavoriteFoodService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/food-categories")
@RequiredArgsConstructor
public class FoodCategoryController {

    private final FavoriteFoodService favoriteFoodService;

    @GetMapping
    public List<FoodCategoryResponse> list() {
        return favoriteFoodService.listCategories();
    }
}
