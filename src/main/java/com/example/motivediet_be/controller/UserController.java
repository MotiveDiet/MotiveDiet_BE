package com.example.motivediet_be.controller;

import com.example.motivediet_be.dto.CoachingSettingsRequest;
import com.example.motivediet_be.dto.CoachingSettingsResponse;
import com.example.motivediet_be.dto.ConsentResponse;
import com.example.motivediet_be.dto.OnboardingRequest;
import com.example.motivediet_be.dto.OnboardingResponse;
import com.example.motivediet_be.service.OnboardingService;
import com.example.motivediet_be.service.UserSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final OnboardingService onboardingService;
    private final UserSettingsService userSettingsService;

    @PatchMapping("/onboarding")
    public OnboardingResponse onboarding(Principal principal, @RequestBody OnboardingRequest request) {
        return onboardingService.onboard(Long.parseLong(principal.getName()), request);
    }

    @GetMapping("/coaching-settings")
    public CoachingSettingsResponse getCoachingSettings(Principal principal) {
        return userSettingsService.getCoachingSettings(Long.parseLong(principal.getName()));
    }

    @PatchMapping("/coaching-settings")
    public CoachingSettingsResponse updateCoachingSettings(Principal principal, @RequestBody CoachingSettingsRequest request) {
        return userSettingsService.updateCoachingSettings(Long.parseLong(principal.getName()), request);
    }

    @PatchMapping("/consent")
    public ConsentResponse consent(Principal principal) {
        return userSettingsService.consent(Long.parseLong(principal.getName()));
    }
}
