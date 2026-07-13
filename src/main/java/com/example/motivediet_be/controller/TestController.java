package com.example.motivediet_be.controller;

import com.example.motivediet_be.domain.User;
import com.example.motivediet_be.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
public class TestController {

    private final AuthService authService;

    @GetMapping("/test")
    public User test(Principal principal) {
        return authService.test(principal);
    }
}