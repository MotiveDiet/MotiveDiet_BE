package com.example.motivediet_be.service;

import com.example.motivediet_be.domain.FavoriteFood;
import com.example.motivediet_be.domain.FoodCategory;
import com.example.motivediet_be.dto.FavoriteFoodResponse;
import com.example.motivediet_be.dto.FoodCategoryResponse;
import com.example.motivediet_be.exception.ApiException;
import com.example.motivediet_be.repository.FavoriteFoodRepository;
import com.example.motivediet_be.repository.FoodCategoryRepository;
import com.example.motivediet_be.repository.FoodLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteFoodService {

    static final int SLOT_CAPACITY = 5;

    private final FavoriteFoodRepository favoriteFoodRepository;
    private final FoodCategoryRepository foodCategoryRepository;
    private final FoodLogRepository foodLogRepository;

    public List<FoodCategoryResponse> listCategories() {
        return foodCategoryRepository.findAll().stream()
                .map(FoodCategoryResponse::from)
                .toList();
    }

    public FavoriteFoodResponse add(Long userId, Long foodCategoryId) {
        FoodCategory category = requireCategory(foodCategoryId);

        // 동시 추가(모바일 더블탭)는 같은 slot_order 를 노려 UNIQUE 위반으로 500을 낼 수 있다.
        // DB 제약을 진실의 원천으로 삼아, 위반 시 재조회 후 재시도한다 — 매 반복 첫머리에서
        // 실제 5개인지 확인하므로, 슬롯이 차 있으면 FAVORITE_SLOT_FULL(400), 아니면 다음 빈 슬롯.
        // 용량이 5라 동시 경쟁자도 최대 5명 → SLOT_CAPACITY 회 재시도면 반드시 수렴한다.
        for (int attempt = 0; attempt < SLOT_CAPACITY; attempt++) {
            List<FavoriteFood> existing = favoriteFoodRepository.findByUserIdOrderBySlotOrder(userId);
            if (existing.size() >= SLOT_CAPACITY) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FAVORITE_SLOT_FULL");
            }
            try {
                FavoriteFood saved = favoriteFoodRepository.saveAndFlush(FavoriteFood.builder()
                        .userId(userId)
                        .foodCategoryId(foodCategoryId)
                        .slotOrder(smallestFreeSlot(existing))
                        .build());
                return toResponse(userId, saved, category);
            } catch (DataIntegrityViolationException e) {
                // 경쟁에서 졌다 — 루프가 재조회 후 다시 시도한다
            }
        }
        // SLOT_CAPACITY회 모두 경쟁에서 지는 건 사실상 없다. 이 시점이면 슬롯이 찬 것으로 본다.
        throw new ApiException(HttpStatus.BAD_REQUEST, "FAVORITE_SLOT_FULL");
    }

    public FavoriteFoodResponse changeCategory(Long userId, Long favoriteFoodId, Long foodCategoryId) {
        FoodCategory category = requireCategory(foodCategoryId);
        FavoriteFood favorite = requireOwned(userId, favoriteFoodId);
        favorite.changeCategory(foodCategoryId);
        favoriteFoodRepository.save(favorite);

        return toResponse(userId, favorite, category);
    }

    public void delete(Long userId, Long favoriteFoodId) {
        FavoriteFood favorite = requireOwned(userId, favoriteFoodId);
        favoriteFoodRepository.delete(favorite);
    }

    private FoodCategory requireCategory(Long foodCategoryId) {
        return foodCategoryRepository.findById(foodCategoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FOOD_CATEGORY"));
    }

    private FavoriteFood requireOwned(Long userId, Long favoriteFoodId) {
        return favoriteFoodRepository.findByIdAndUserId(favoriteFoodId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FAVORITE_NOT_FOUND"));
    }

    // 삭제로 생긴 빈 슬롯을 0~4 중 가장 작은 것부터 채운다 (슬롯 순서 불변식 유지).
    private int smallestFreeSlot(List<FavoriteFood> existing) {
        Set<Integer> used = existing.stream().map(FavoriteFood::getSlotOrder).collect(Collectors.toSet());
        for (int i = 0; i < SLOT_CAPACITY; i++) {
            if (!used.contains(i)) {
                return i;
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "FAVORITE_SLOT_FULL");
    }

    private FavoriteFoodResponse toResponse(Long userId, FavoriteFood favorite, FoodCategory category) {
        long weeklyCount = foodLogRepository.countByUserIdAndFoodCategoryIdAndLoggedAtGreaterThanEqual(
                userId, category.getId(), LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay());
        return new FavoriteFoodResponse(
                favorite.getId(), category.getId(), category.getName(), category.getEmoji(),
                weeklyCount, category.getWeeklyThreshold(), favorite.getSlotOrder());
    }
}
