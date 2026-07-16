package com.example.motivediet_be.domain;

import jakarta.persistence.*;
import lombok.*;

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
