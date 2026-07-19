package com.example.motivediet_be.controller;

import com.example.motivediet_be.dto.FoodLogRequest;
import com.example.motivediet_be.dto.FoodLogResponse;
import com.example.motivediet_be.service.FoodLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/food-logs")
@RequiredArgsConstructor
public class FoodLogController {

    private final FoodLogService foodLogService;

    @PostMapping
    public ResponseEntity<FoodLogResponse> log(Principal principal, @RequestBody FoodLogRequest request) {
        FoodLogResponse created = foodLogService.log(Long.parseLong(principal.getName()), request.favoriteFoodId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
