package com.example.motivediet_be.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 온보딩 동기 자유텍스트를 파싱한 구조화 신호. 원문(motiveText)은 저장하지 않는다 —
 * paraphrase는 원문을 복원할 수 없는 수준의 요약이다 (PRD 5절).
 */
@Entity
@Table(name = "motive_signal")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MotiveSignal {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "motive_type", nullable = false)
    private MotiveType motiveType;

    @Column(name = "target")
    private String target;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(name = "paraphrase")
    private String paraphrase;
}
