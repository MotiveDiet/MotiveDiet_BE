package com.example.motivediet_be.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class User {
    @Id
    @Column(name = "USER_ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USER_GOOGLE_ID", unique = true)
    private String googleId;

    @Column(name = "USER_NAME", nullable = false)
    private String name;

    @Column(name = "USER_EMAIL", nullable = false)
    private String email;

    @Column(name = "USER_PICTURE", nullable = false)
    private String pictureUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "USER_ROLE", nullable = false)
    private Role role;

    @Column(name = "USER_GOAL_WEIGHT")
    private Double goalWeight;

    @Column(name = "USER_GOAL_DATE")
    private LocalDate goalDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "USER_INTENSITY_LEVEL", nullable = false)
    private IntensityLevel intensityLevel = IntensityLevel.MILD;

    @Builder.Default
    @Column(name = "USER_FREQUENCY_LAYER_ENABLED", nullable = false)
    private boolean frequencyLayerEnabled = true;

    @Builder.Default
    @Column(name = "USER_MOTIVE_COMBO_ENABLED", nullable = false)
    private boolean motiveComboEnabled = true;

    // null = 미동의. 코칭 API(POST /api/food-logs)는 이 값이 null이면 403.
    @Column(name = "USER_CONSENTED_AT")
    private LocalDateTime consentedAt;

    public void updateOnboarding(Double goalWeight, LocalDate goalDate) {
        this.goalWeight = goalWeight;
        this.goalDate = goalDate;
    }

    // PATCH 코칭 설정: 보낸 필드만 갱신(null은 그대로 유지).
    public void updateCoachingSettings(IntensityLevel intensityLevel, Boolean frequencyLayerEnabled, Boolean motiveComboEnabled) {
        if (intensityLevel != null) {
            this.intensityLevel = intensityLevel;
        }
        if (frequencyLayerEnabled != null) {
            this.frequencyLayerEnabled = frequencyLayerEnabled;
        }
        if (motiveComboEnabled != null) {
            this.motiveComboEnabled = motiveComboEnabled;
        }
    }

    public void consent(LocalDateTime at) {
        this.consentedAt = at;
    }

    public void connectGoogleId(String googleId) {
        if (this.googleId == null) {
            this.googleId = googleId;

            return;
        }

        if (!this.googleId.equals(googleId)) {
            throw new RuntimeException("이미 다른 구글 계정과 연결된 유저입니다.");
        }
    }
}
