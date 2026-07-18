package com.example.motivediet_be.dto;

import com.example.motivediet_be.domain.MotiveType;

/**
 * gpt-5-mini Structured Outputs가 돌려주는 파싱 결과. eventDate는 "2026-09-01" 또는 null 문자열이라
 * String으로 받고 서비스에서 LocalDate로 변환한다.
 */
public record MotiveParseResult(MotiveType motiveType, String target, String eventDate, String paraphrase) {
}
