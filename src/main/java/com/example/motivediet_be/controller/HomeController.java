package com.example.motivediet_be.controller;

import com.example.motivediet_be.dto.HomeResponse;
import com.example.motivediet_be.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    public HomeResponse home(Principal principal) {
        return homeService.home(Long.parseLong(principal.getName()));
    }
}
