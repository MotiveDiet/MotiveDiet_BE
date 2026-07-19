package com.example.motivediet_be.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeServiceStreakTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 18);

    @Test
    @DisplayName("로그가 없으면 스트릭은 0")
    void 로그없음() {
        assertEquals(0, HomeService.currentStreak(Set.of(), TODAY));
    }

    @Test
    @DisplayName("오늘 포함 연속으로 이어지면 그 길이를 센다")
    void 오늘부터_연속() {
        Set<LocalDate> dates = Set.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2));
        assertEquals(3, HomeService.currentStreak(dates, TODAY));
    }

    @Test
    @DisplayName("오늘이 비어 있어도 스트릭을 끊지 않고 어제부터 센다")
    void 오늘_비어도_유지() {
        Set<LocalDate> dates = Set.of(TODAY.minusDays(1), TODAY.minusDays(2));
        assertEquals(2, HomeService.currentStreak(dates, TODAY));
    }

    @Test
    @DisplayName("오늘도 어제도 비어 있으면 스트릭은 0")
    void 이틀_연속_비면_끊김() {
        Set<LocalDate> dates = Set.of(TODAY.minusDays(2), TODAY.minusDays(3));
        assertEquals(0, HomeService.currentStreak(dates, TODAY));
    }
}
