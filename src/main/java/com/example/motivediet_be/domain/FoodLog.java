package com.example.motivediet_be.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 원탭 로깅 1건. 출석(스트릭) 판정과 음식 빈도 판정의 원천 데이터.
 * 스트릭/빈도는 별도 테이블 없이 이 테이블을 매 요청 즉석 집계한다.
 */
@Entity
@Table(name = "food_log")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FoodLog {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "food_category_id", nullable = false)
    private Long foodCategoryId;

    @Column(name = "logged_at", nullable = false)
    private LocalDateTime loggedAt;
}
