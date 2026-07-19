package com.example.motivediet_be.dto;

import java.time.LocalDate;
import java.util.List;

public record HomeResponse(
        LocalDate today,
        MotiveSignalResponse motiveSignal,
        List<DayStreak> weekStreak,
        int currentStreakDays,
        List<FavoriteFoodResponse> favoriteFoods,
        int favoriteSlotCapacity) {

    // 동기 칩. 유효한 신호가 없으면 상위 필드가 null이라 칩 자체가 숨겨진다.
    public record MotiveSignalResponse(String emoji, String label, Integer daysUntil) {
    }

    public record DayStreak(LocalDate date, String dayOfWeek, boolean logged) {
    }
}
