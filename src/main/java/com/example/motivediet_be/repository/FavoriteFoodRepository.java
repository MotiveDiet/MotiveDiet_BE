package com.example.motivediet_be.repository;

import com.example.motivediet_be.domain.FavoriteFood;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteFoodRepository extends JpaRepository<FavoriteFood, Long> {

    List<FavoriteFood> findByUserIdOrderBySlotOrder(Long userId);

    // 소유권 확인용 — 다른 사용자의 슬롯을 건드리지 못하게 userId를 조건에 포함한다.
    Optional<FavoriteFood> findByIdAndUserId(Long id, Long userId);
}
