package com.example.motivediet_be.dto;

import java.time.LocalDateTime;

/**
 * Phase 1에서는 로깅 결과만 반환한다. 팩폭(coachMessage)은 Phase 2에서 같은 응답에 추가된다.
 */
public record FoodLogResponse(
        Long foodLogId,
        LocalDateTime loggedAt,
        FoodCategoryBrief foodCategory,
        long weeklyCount) {

    public record FoodCategoryBrief(String name, String emoji) {
    }
}
