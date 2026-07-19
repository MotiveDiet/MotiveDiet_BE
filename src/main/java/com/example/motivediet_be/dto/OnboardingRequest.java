package com.example.motivediet_be.dto;

import java.time.LocalDate;

public record OnboardingRequest(Double goalWeight, LocalDate goalDate, String motiveText) {
}
