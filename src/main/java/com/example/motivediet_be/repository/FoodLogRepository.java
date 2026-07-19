package com.example.motivediet_be.repository;

import com.example.motivediet_be.domain.FoodLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface FoodLogRepository extends JpaRepository<FoodLog, Long> {

    // 스트릭 계산용 — 사용자의 로그가 존재하는 날짜(중복 제거). 주 경계를 넘길 수 있어 전체를 본다.
    @Query("SELECT DISTINCT CAST(f.loggedAt AS date) FROM FoodLog f WHERE f.userId = :userId")
    List<LocalDate> findLogDates(@Param("userId") Long userId);

    // 이번 주 로그 — 홈의 요일별 불꽃 표시와 카테고리별 주간 카운트를 한 번에 집계한다.
    List<FoodLog> findByUserIdAndLoggedAtGreaterThanEqual(Long userId, LocalDateTime from);

    // 특정 카테고리의 이번 주 카운트 — 로깅 응답의 weeklyCount.
    long countByUserIdAndFoodCategoryIdAndLoggedAtGreaterThanEqual(Long userId, Long foodCategoryId, LocalDateTime from);
}
