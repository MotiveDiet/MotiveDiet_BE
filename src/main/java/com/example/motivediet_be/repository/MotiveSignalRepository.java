package com.example.motivediet_be.repository;

import com.example.motivediet_be.domain.MotiveSignal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MotiveSignalRepository extends JpaRepository<MotiveSignal, Long> {

    // 온보딩을 다시 하면 새 신호가 쌓이므로, 홈은 가장 최근 것을 본다.
    Optional<MotiveSignal> findFirstByUserIdOrderByIdDesc(Long userId);
}
