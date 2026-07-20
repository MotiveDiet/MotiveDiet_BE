package com.example.motivediet_be.domain;

/**
 * 코칭 팩폭 강도. OFF면 generateCoachMessage가 LLM 호출 자체를 스킵한다(코칭 설정 화면 1d).
 */
public enum IntensityLevel {
    OFF, MILD, MEDIUM, STRONG
}
