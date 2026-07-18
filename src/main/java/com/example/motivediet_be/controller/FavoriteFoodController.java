package com.example.motivediet_be.controller;

import com.example.motivediet_be.dto.FavoriteFoodRequest;
import com.example.motivediet_be.dto.FavoriteFoodResponse;
import com.example.motivediet_be.service.FavoriteFoodService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/favorite-foods")
@RequiredArgsConstructor
public class FavoriteFoodController {

    private final FavoriteFoodService favoriteFoodService;

    @PostMapping
    public ResponseEntity<FavoriteFoodResponse> add(Principal principal, @RequestBody FavoriteFoodRequest request) {
        FavoriteFoodResponse created = favoriteFoodService.add(userId(principal), request.foodCategoryId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{favoriteFoodId}")
    public FavoriteFoodResponse change(Principal principal,
                                       @PathVariable Long favoriteFoodId,
                                       @RequestBody FavoriteFoodRequest request) {
        return favoriteFoodService.changeCategory(userId(principal), favoriteFoodId, request.foodCategoryId());
    }

    @DeleteMapping("/{favoriteFoodId}")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long favoriteFoodId) {
        favoriteFoodService.delete(userId(principal), favoriteFoodId);
        return ResponseEntity.noContent().build();
    }

    private Long userId(Principal principal) {
        return Long.parseLong(principal.getName());
    }
}
