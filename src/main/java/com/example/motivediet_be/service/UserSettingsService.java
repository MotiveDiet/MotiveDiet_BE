package com.example.motivediet_be.service;

import com.example.motivediet_be.domain.User;
import com.example.motivediet_be.dto.CoachingSettingsRequest;
import com.example.motivediet_be.dto.CoachingSettingsResponse;
import com.example.motivediet_be.dto.ConsentResponse;
import com.example.motivediet_be.dto.UserMeResponse;
import com.example.motivediet_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * /api/users/me 아래 코칭 설정(화면 1d)과 opt-in 동의(API.md 2·6절) 담당.
 */
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        return UserMeResponse.from(findUser(userId));
    }

    @Transactional(readOnly = true)
    public CoachingSettingsResponse getCoachingSettings(Long userId) {
        return CoachingSettingsResponse.from(findUser(userId));
    }

    @Transactional
    public CoachingSettingsResponse updateCoachingSettings(Long userId, CoachingSettingsRequest request) {
        User user = findUser(userId);
        user.updateCoachingSettings(request.intensityLevel(), request.frequencyLayerEnabled(), request.motiveComboEnabled());
        return CoachingSettingsResponse.from(user);
    }

    @Transactional
    public ConsentResponse consent(Long userId) {
        User user = findUser(userId);
        user.consent(LocalDateTime.now());
        return new ConsentResponse(user.getConsentedAt());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));
    }
}
