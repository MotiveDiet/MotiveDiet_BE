package com.example.motivediet_be.dto;

import com.example.motivediet_be.domain.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * GET /api/users/me 응답. 로그인 직후 동의화면·온보딩·홈 중 어디로 보낼지 판별하는 용도.
 */
public record UserMeResponse(
        LocalDateTime consentedAt,
        Double goalWeight,
        LocalDate goalDate,
        boolean onboarded) {

    public static UserMeResponse from(User user) {
        return new UserMeResponse(
                user.getConsentedAt(),
                user.getGoalWeight(),
                user.getGoalDate(),
                user.getGoalWeight() != null && user.getGoalDate() != null);
    }
}
