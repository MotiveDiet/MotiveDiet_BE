package com.example.motivediet_be.service;

import com.example.motivediet_be.domain.FavoriteFood;
import com.example.motivediet_be.domain.FoodCategory;
import com.example.motivediet_be.domain.FoodLog;
import com.example.motivediet_be.dto.FoodLogResponse;
import com.example.motivediet_be.exception.ApiException;
import com.example.motivediet_be.repository.FavoriteFoodRepository;
import com.example.motivediet_be.repository.FoodCategoryRepository;
import com.example.motivediet_be.repository.FoodLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FoodLogService {

    private final FavoriteFoodRepository favoriteFoodRepository;
    private final FoodCategoryRepository foodCategoryRepository;
    private final FoodLogRepository foodLogRepository;

    public FoodLogResponse log(Long userId, Long favoriteFoodId) {
        FavoriteFood favorite = favoriteFoodRepository.findByIdAndUserId(favoriteFoodId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FAVORITE_NOT_FOUND"));

        FoodLog saved = foodLogRepository.save(FoodLog.builder()
                .userId(userId)
                .foodCategoryId(favorite.getFoodCategoryId())
                .loggedAt(LocalDateTime.now())
                .build());

        long weeklyCount = foodLogRepository.countByUserIdAndFoodCategoryIdAndLoggedAtGreaterThanEqual(
                userId, favorite.getFoodCategoryId(), LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay());

        FoodCategory category = foodCategoryRepository.findById(favorite.getFoodCategoryId())
                .orElseThrow(() -> new RuntimeException("음식 카테고리를 찾을 수 없습니다."));

        // Phase 1은 로깅 결과만 반환한다. coachMessage(팩폭)는 Phase 2에서 이 응답에 추가된다.
        return new FoodLogResponse(
                saved.getId(),
                saved.getLoggedAt(),
                new FoodLogResponse.FoodCategoryBrief(category.getName(), category.getEmoji()),
                weeklyCount);
    }
}
