package com.example.motivediet_be.repository;

import com.example.motivediet_be.domain.FoodLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FoodLogRepository extends JpaRepository<FoodLog, Long> {

    // 스트릭 계산용 — 사용자의 로그 시각 전체. 날짜 변환은 서비스에서 한다.
    //
    // 주의: `SELECT DISTINCT CAST(f.loggedAt AS date)` 를 `List<LocalDate>` 로 받으면 안 된다.
    // Hibernate 가 java.sql.Date 를 돌려주고 Spring Data 가 LocalDate 로 변환하지 못해
    // ConverterNotFoundException → 500 이 난다. 로그가 0건이면 리스트가 비어 변환이 일어나지
    // 않으므로 빈 계정에서는 멀쩡해 보인다 (2026-07-21 프로덕션 장애).
    @Query("SELECT f.loggedAt FROM FoodLog f WHERE f.userId = :userId")
    List<LocalDateTime> findLoggedAtByUserId(@Param("userId") Long userId);

    // 이번 주 로그 — 홈의 요일별 불꽃 표시와 카테고리별 주간 카운트를 한 번에 집계한다.
    List<FoodLog> findByUserIdAndLoggedAtGreaterThanEqual(Long userId, LocalDateTime from);

    // 특정 카테고리의 이번 주 카운트 — 로깅 응답의 weeklyCount.
    long countByUserIdAndFoodCategoryIdAndLoggedAtGreaterThanEqual(Long userId, Long foodCategoryId, LocalDateTime from);
}
