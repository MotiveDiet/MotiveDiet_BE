package com.example.motivediet_be.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * POST /api/food-logs 응답에 실리는 팩폭 코칭 메시지 (API.md 5절).
 * motiveCombo는 유효 동기가 D-14 이내일 때만 존재하고, 아니면 필드 자체가 생략된다(null 아님).
 */
public record CoachMessage(
        String toneType,
        String text,
        @JsonInclude(JsonInclude.Include.NON_NULL) MotiveCombo motiveCombo) {

    public record MotiveCombo(String text, int daysUntil) {
    }
}
