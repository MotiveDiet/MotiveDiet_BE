package com.example.motivediet_be.controller;

import com.example.motivediet_be.dto.TokenDto;
import com.example.motivediet_be.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RequestMapping("/api/oauth2")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/login/google")
    public RedirectView googleLogin() {
        return new RedirectView(authService.getGoogleLoginUrl());
    }

    @GetMapping("/callback/google")
    public TokenDto googleCallback(@RequestParam("code") String code) {

        String googleAccessToken = authService.getGoogleAccessToken(code); // google 토큰 받기

        return authService.loginOrSignUp(googleAccessToken); // TokenDto로 변환
    }

}

