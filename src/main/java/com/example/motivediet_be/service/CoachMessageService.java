package com.example.motivediet_be.service;

import com.example.motivediet_be.domain.FoodCategory;
import com.example.motivediet_be.domain.IntensityLevel;
import com.example.motivediet_be.domain.MotiveSignal;
import com.example.motivediet_be.domain.User;
import com.example.motivediet_be.dto.CoachMessage;
import com.example.motivediet_be.repository.MotiveSignalRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * POST /api/food-logs 저장 직후 팩폭 한 문장을 생성한다 (gpt-5 호출 1회).
 * ROADMAP Phase 2/3: 음식명 + 콘텐츠 가이드라인은 항상, 유효 동기가 있으면 target/paraphrase,
 * D-14 이내면 D-day 카운트다운, 이번 주 카운트가 임계 이상이면 빈도 컨텍스트까지 프롬프트에 얹는다.
 */
@Service
@RequiredArgsConstructor
public class CoachMessageService {

    private static final Logger log = LoggerFactory.getLogger(CoachMessageService.class);
    private static final String TONE_FACTBOMB = "FACTBOMB";
    private static final int DDAY_WINDOW = 14;

    private final MotiveSignalRepository motiveSignalRepository;
    private final OpenAiClient openAiClient;

    /**
     * 강도 OFF면 LLM 호출을 스킵하고 null. 생성 실패도 로깅만 하고 null(로그 저장 자체는 이미 끝났다).
     * weeklyCount는 방금 저장분까지 포함한 이번 주 카운트(응답 chip과 동일 값). Phase 3 빈도 레이어 판정에 쓴다.
     */
    public CoachMessage generate(User user, FoodCategory category, long weeklyCount) {
        if (user.getIntensityLevel() == IntensityLevel.OFF) {
            return null;
        }

        MotiveSignal signal = motiveSignalRepository.findFirstByUserIdOrderByIdDesc(user.getId()).orElse(null);
        LocalDate today = LocalDate.now();
        // 날짜가 지나지 않았고(또는 날짜 없음) 실제 내용이 있는 동기만 유효로 본다.
        // 파싱 스키마가 빈 문자열 target/paraphrase 를 허용하므로 hasText 로 걸러야 내용 없는 D-day 콤보를 막는다.
        boolean dateValid = signal != null
                && (signal.getEventDate() == null || !signal.getEventDate().isBefore(today));
        String target = dateValid && StringUtils.hasText(signal.getTarget()) ? signal.getTarget() : null;
        String paraphrase = dateValid && StringUtils.hasText(signal.getParaphrase()) ? signal.getParaphrase() : null;
        boolean hasMotiveContent = target != null || paraphrase != null;
        Integer daysUntil = hasMotiveContent ? motiveComboDaysUntil(user, signal, today) : null;

        // Phase 3: 레이어가 켜져 있고 이번 주 카운트가 임계 이상이면 실제 카운트를 프롬프트에 싣는다. 아니면 null.
        Integer frequencyCount = (user.isFrequencyLayerEnabled() && weeklyCount >= category.getWeeklyThreshold())
                ? (int) weeklyCount : null;

        try {
            OpenAiClient.CoachResult result = openAiClient.generateCoachMessage(
                    category.getName(), user.getIntensityLevel(), target, paraphrase, daysUntil, frequencyCount);

            CoachMessage.MotiveCombo combo = (daysUntil != null && StringUtils.hasText(result.motiveComboText()))
                    ? new CoachMessage.MotiveCombo(result.motiveComboText(), daysUntil)
                    : null;
            return new CoachMessage(TONE_FACTBOMB, result.text(), combo);
        } catch (RuntimeException e) {
            log.warn("팩폭 생성 실패 — 로그는 저장됨, 메시지 없이 반환: {}", e.getMessage());
            return null;
        }
    }

    // motiveComboEnabled + 유효 동기 + 날짜 존재 + 0~14일 이내일 때만 D-day. 아니면 null.
    private Integer motiveComboDaysUntil(User user, MotiveSignal signal, LocalDate today) {
        if (!user.isMotiveComboEnabled() || signal == null || signal.getEventDate() == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(today, signal.getEventDate());
        return (days >= 0 && days <= DDAY_WINDOW) ? (int) days : null;
    }
}
