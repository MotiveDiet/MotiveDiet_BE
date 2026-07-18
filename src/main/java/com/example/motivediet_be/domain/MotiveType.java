package com.example.motivediet_be.domain;

/**
 * 온보딩 동기 파싱이 뽑아내는 이벤트 유형. 홈 대시보드 칩의 이모지를 이 enum에서 끌어온다.
 * gpt-5-mini Structured Outputs가 아래 상수명 중 하나를 강제로 고르게 한다.
 */
public enum MotiveType {
    ANNIVERSARY("🎂"),
    EVENT("📅"),
    TRAVEL("✈️"),
    HEALTH("💪"),
    APPEARANCE("✨"),
    OTHER("🎯");

    private final String emoji;

    MotiveType(String emoji) {
        this.emoji = emoji;
    }

    public String emoji() {
        return emoji;
    }
}
