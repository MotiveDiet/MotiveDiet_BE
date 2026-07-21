package com.example.motivediet_be.dto;

import com.example.motivediet_be.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserMeResponseTest {

    private static User user(Double goalWeight, LocalDate goalDate) {
        return User.builder().goalWeight(goalWeight).goalDate(goalDate).build();
    }

    @Test
    @DisplayName("목표 체중과 목표 날짜가 모두 있으면 온보딩 완료")
    void 둘_다_있으면_완료() {
        assertTrue(UserMeResponse.from(user(68.0, LocalDate.of(2026, 9, 1))).onboarded());
    }

    @Test
    @DisplayName("하나라도 비면 온보딩 미완료")
    void 하나라도_없으면_미완료() {
        assertFalse(UserMeResponse.from(user(68.0, null)).onboarded());
        assertFalse(UserMeResponse.from(user(null, LocalDate.of(2026, 9, 1))).onboarded());
        assertFalse(UserMeResponse.from(user(null, null)).onboarded());
    }
}
