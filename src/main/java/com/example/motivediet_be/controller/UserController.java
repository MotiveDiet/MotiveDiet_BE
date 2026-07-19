package com.example.motivediet_be.controller;

import com.example.motivediet_be.dto.OnboardingRequest;
import com.example.motivediet_be.dto.OnboardingResponse;
import com.example.motivediet_be.service.OnboardingService;
import lombok.RequiredArgsConstructor;
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

    @PatchMapping("/onboarding")
    public OnboardingResponse onboarding(Principal principal, @RequestBody OnboardingRequest request) {
        return onboardingService.onboard(Long.parseLong(principal.getName()), request);
    }
}
