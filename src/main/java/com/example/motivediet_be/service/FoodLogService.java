package com.example.motivediet_be.service;

import com.example.motivediet_be.domain.FavoriteFood;
import com.example.motivediet_be.domain.FoodCategory;
import com.example.motivediet_be.domain.FoodLog;
import com.example.motivediet_be.domain.User;
import com.example.motivediet_be.dto.CoachMessage;
import com.example.motivediet_be.dto.FoodLogResponse;
import com.example.motivediet_be.exception.ApiException;
import com.example.motivediet_be.repository.FavoriteFoodRepository;
import com.example.motivediet_be.repository.FoodCategoryRepository;
import com.example.motivediet_be.repository.FoodLogRepository;
import com.example.motivediet_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FoodLogService {

    private final FavoriteFoodRepository favoriteFoodRepository;
    private final FoodCategoryRepository foodCategoryRepository;
    private final FoodLogRepository foodLogRepository;
    private final UserRepository userRepository;
    private final CoachMessageService coachMessageService;

    public FoodLogResponse log(Long userId, Long favoriteFoodId) {
        FavoriteFood favorite = favoriteFoodRepository.findByIdAndUserId(favoriteFoodId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FAVORITE_NOT_FOUND"));

        LocalDateTime loggedAt = LocalDateTime.now();
        FoodLog saved = foodLogRepository.save(FoodLog.builder()
                .userId(userId)
                .foodCategoryId(favorite.getFoodCategoryId())
                .loggedAt(loggedAt)
                .build());

        long weeklyCount = foodLogRepository.countByUserIdAndFoodCategoryIdAndLoggedAtGreaterThanEqual(
                userId, favorite.getFoodCategoryId(), loggedAt.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay());

        FoodCategory category = foodCategoryRepository.findById(favorite.getFoodCategoryId())
                .orElseThrow(() -> new RuntimeException("음식 카테고리를 찾을 수 없습니다."));

        // 미동의는 ConsentInterceptor가 먼저 403으로 막으므로 여기 도달하면 동의된 사용자다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));
        CoachMessage coachMessage = coachMessageService.generate(user, category, weeklyCount);

        return new FoodLogResponse(
                saved.getId(),
                saved.getLoggedAt(),
                new FoodLogResponse.FoodCategoryBrief(category.getName(), category.getEmoji()),
                weeklyCount,
                coachMessage);
    }
}
