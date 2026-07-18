package com.example.motivediet_be.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 살찌는 음식 화이트리스트. 이 테이블에 존재하는 것 자체가 "살찌는 음식"이고,
 * weeklyThreshold는 Phase 3 빈도 판정에 쓰인다. 값은 data.sql로 고정 시드된다.
 */
@Entity
@Table(name = "food_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodCategory {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "emoji", nullable = false)
    private String emoji;

    @Column(name = "weekly_threshold", nullable = false)
    private int weeklyThreshold;
}
