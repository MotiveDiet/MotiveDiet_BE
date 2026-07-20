package com.example.motivediet_be.dto;

import com.example.motivediet_be.domain.IntensityLevel;
import com.example.motivediet_be.domain.User;

/**
 * GET/PATCH /api/users/me/coaching-settings 응답 (API.md 6절).
 * lockScreenEnabled는 항상 false인 읽기 전용 값이다(정책상 끌 수 없음).
 */
public record CoachingSettingsResponse(
        IntensityLevel intensityLevel,
        boolean frequencyLayerEnabled,
        boolean motiveComboEnabled,
        boolean lockScreenEnabled) {

    public static CoachingSettingsResponse from(User user) {
        return new CoachingSettingsResponse(
                user.getIntensityLevel(),
                user.isFrequencyLayerEnabled(),
                user.isMotiveComboEnabled(),
                false);
    }
}
