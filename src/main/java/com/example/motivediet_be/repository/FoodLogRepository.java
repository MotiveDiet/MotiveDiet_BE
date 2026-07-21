package com.example.motivediet_be.repository;

import com.example.motivediet_be.domain.FoodLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FoodLogRepository extends JpaRepository<FoodLog, Long> {

    // 스트릭 계산용 — 로그가 있는 날짜(DB에서 중복 제거). 주 경계를 넘길 수 있어 전체를 본다.
    //
    // 반환 타입이 java.sql.Date 인 게 핵심이다. Hibernate 가 CAST(... AS date) 결과로
    // java.sql.Date 를 주는데, 이걸 List<LocalDate> 로 선언하면 Spring Data 가 변환하지 못해
    // ConverterNotFoundException -> 500 이 난다. 로그가 0건이면 변환이 일어나지 않아
    // 빈 계정에서는 멀쩡해 보인다 (2026-07-21 프로덕션 장애). 날짜 변환은 서비스에서 한다.
    @Query("SELECT DISTINCT CAST(f.loggedAt AS date) FROM FoodLog f WHERE f.userId = :userId")
    List<java.sql.Date> findLogDates(@Param("userId") Long userId);

    // 이번 주 로그 — 홈의 요일별 불꽃 표시와 카테고리별 주간 카운트를 한 번에 집계한다.
    List<FoodLog> findByUserIdAndLoggedAtGreaterThanEqual(Long userId, LocalDateTime from);

    // 특정 카테고리의 이번 주 카운트 — 로깅 응답의 weeklyCount.
    long countByUserIdAndFoodCategoryIdAndLoggedAtGreaterThanEqual(Long userId, Long foodCategoryId, LocalDateTime from);
}
