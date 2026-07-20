package com.example.motivediet_be.dto;

import java.time.LocalDateTime;

/** PATCH /api/users/me/consent 응답 (API.md 2절). */
public record ConsentResponse(LocalDateTime consentedAt) {
}
