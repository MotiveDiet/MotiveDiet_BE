package com.example.motivediet_be.dto;

import java.time.LocalDateTime;

/**
 * 원탭 로깅 응답. coachMessage는 팩폭(Phase 2) — 강도 OFF이거나 생성 실패 시 null.
 */
public record FoodLogResponse(
        Long foodLogId,
        LocalDateTime loggedAt,
        FoodCategoryBrief foodCategory,
        long weeklyCount,
        CoachMessage coachMessage) {

    public record FoodCategoryBrief(String name, String emoji) {
    }
}
