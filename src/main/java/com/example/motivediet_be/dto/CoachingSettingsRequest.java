package com.example.motivediet_be.dto;

import com.example.motivediet_be.domain.IntensityLevel;

/**
 * PATCH /api/users/me/coaching-settings — 바꾸고 싶은 필드만 보낸다(나머지는 null → 유지).
 * lockScreenEnabled는 정책상 항상 꺼짐이라 PATCH 대상이 아니다(요청에 없음).
 */
public record CoachingSettingsRequest(
        IntensityLevel intensityLevel,
        Boolean frequencyLayerEnabled,
        Boolean motiveComboEnabled) {
}
