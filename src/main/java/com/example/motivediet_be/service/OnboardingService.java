package com.example.motivediet_be.service;

import com.example.motivediet_be.domain.MotiveSignal;
import com.example.motivediet_be.domain.User;
import com.example.motivediet_be.dto.MotiveParseResult;
import com.example.motivediet_be.dto.OnboardingRequest;
import com.example.motivediet_be.dto.OnboardingResponse;
import com.example.motivediet_be.repository.MotiveSignalRepository;
import com.example.motivediet_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserRepository userRepository;
    private final MotiveSignalRepository motiveSignalRepository;
    private final OpenAiClient openAiClient;

    /**
     * 목표 갱신과 동기 파싱을 한 트랜잭션에서 처리한다. 파싱이 실패하면 전체가 롤백된다.
     * motiveText 원문은 이 메서드 지역 변수로만 존재하고 DB/로그 어디에도 남기지 않는다 (PRD 5절).
     */
    @Transactional
    public OnboardingResponse onboard(Long userId, OnboardingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));
        user.updateOnboarding(request.goalWeight(), request.goalDate());

        MotiveParseResult parsed = openAiClient.parseMotive(request.motiveText());
        motiveSignalRepository.save(MotiveSignal.builder()
                .userId(userId)
                .motiveType(parsed.motiveType())
                .target(parsed.target())
                .eventDate(toLocalDate(parsed.eventDate()))
                .paraphrase(parsed.paraphrase())
                .build());

        return new OnboardingResponse(user.getGoalWeight(), user.getGoalDate());
    }

    // LLM이 비-ISO 날짜를 돌려줄 수 있다(Structured Outputs는 형식까지 강제하지 못함).
    // 파싱 실패로 온보딩 전체를 500내지 않고, 카운트다운 없는 동기 신호로 저장한다.
    private LocalDate toLocalDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
