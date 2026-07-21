package com.example.motivediet_be.service;

import com.example.motivediet_be.domain.FoodCategory;
import com.example.motivediet_be.domain.IntensityLevel;
import com.example.motivediet_be.domain.MotiveSignal;
import com.example.motivediet_be.domain.MotiveType;
import com.example.motivediet_be.domain.User;
import com.example.motivediet_be.dto.CoachMessage;
import com.example.motivediet_be.repository.MotiveSignalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoachMessageServiceTest {

    private final MotiveSignalRepository motiveSignalRepository = mock(MotiveSignalRepository.class);
    private final OpenAiClient openAiClient = mock(OpenAiClient.class);
    private final CoachMessageService service = new CoachMessageService(motiveSignalRepository, openAiClient);

    // weeklyThreshold=2 — 아래 동기 테스트는 weeklyCount=1(임계 미만)로 호출해 빈도 컨텍스트를 끈다.
    private FoodCategory category() {
        FoodCategory category = mock(FoodCategory.class);
        when(category.getName()).thenReturn("치킨");
        when(category.getWeeklyThreshold()).thenReturn(2);
        return category;
    }

    private User user(IntensityLevel intensity, boolean motiveComboEnabled) {
        return User.builder().id(1L).intensityLevel(intensity).motiveComboEnabled(motiveComboEnabled).build();
    }

    private void stubLlm(String text, String motiveComboText) {
        when(openAiClient.generateCoachMessage(any(), any(), any(), any(), any(), any()))
                .thenReturn(new OpenAiClient.CoachResult(text, motiveComboText));
    }

    @Test
    @DisplayName("강도 OFF면 LLM을 호출하지 않고 null")
    void off_스킵() {
        assertNull(service.generate(user(IntensityLevel.OFF, true), category(), 1));
        verify(openAiClient, never()).generateCoachMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("유효 동기가 D-14 이내면 target·paraphrase·D-day를 넘기고 motiveCombo를 만든다")
    void 동기_dday_콤보() {
        MotiveSignal signal = MotiveSignal.builder()
                .motiveType(MotiveType.ANNIVERSARY).target("여자친구 생일").paraphrase("생일 전까지 감량")
                .eventDate(LocalDate.now().plusDays(10)).build();
        when(motiveSignalRepository.findFirstByUserIdOrderByIdDesc(1L)).thenReturn(Optional.of(signal));
        stubLlm("치킨 또 먹네", "D-10인데 이럴 거야?");

        CoachMessage message = service.generate(user(IntensityLevel.STRONG, true), category(), 1);

        assertEquals("FACTBOMB", message.toneType());
        assertEquals("치킨 또 먹네", message.text());
        assertEquals(10, message.motiveCombo().daysUntil());
        assertEquals("D-10인데 이럴 거야?", message.motiveCombo().text());

        ArgumentCaptor<Integer> daysUntil = ArgumentCaptor.forClass(Integer.class);
        verify(openAiClient).generateCoachMessage(eq("치킨"), eq(IntensityLevel.STRONG),
                eq("여자친구 생일"), eq("생일 전까지 감량"), daysUntil.capture(), eq(null));
        assertEquals(10, daysUntil.getValue());
    }

    @Test
    @DisplayName("motiveComboEnabled=false면 D-14 이내여도 D-day를 넘기지 않고 콤보도 없다")
    void 콤보_토글_off() {
        MotiveSignal signal = MotiveSignal.builder()
                .motiveType(MotiveType.ANNIVERSARY).target("여자친구 생일").paraphrase("생일 전까지 감량")
                .eventDate(LocalDate.now().plusDays(10)).build();
        when(motiveSignalRepository.findFirstByUserIdOrderByIdDesc(1L)).thenReturn(Optional.of(signal));
        stubLlm("치킨 또 먹네", "무시될 콤보");

        CoachMessage message = service.generate(user(IntensityLevel.STRONG, false), category(), 1);

        assertNull(message.motiveCombo());
        verify(openAiClient).generateCoachMessage(eq("치킨"), any(),
                eq("여자친구 생일"), eq("생일 전까지 감량"), eq(null), eq(null));
    }

    @Test
    @DisplayName("D-14를 벗어난 동기는 target은 넘기되 D-day는 넘기지 않는다")
    void 윈도우_밖() {
        MotiveSignal signal = MotiveSignal.builder()
                .motiveType(MotiveType.ANNIVERSARY).target("여자친구 생일").paraphrase("생일 전까지 감량")
                .eventDate(LocalDate.now().plusDays(15)).build();
        when(motiveSignalRepository.findFirstByUserIdOrderByIdDesc(1L)).thenReturn(Optional.of(signal));
        stubLlm("치킨 또 먹네", null);

        CoachMessage message = service.generate(user(IntensityLevel.MILD, true), category(), 1);

        assertNull(message.motiveCombo());
        verify(openAiClient).generateCoachMessage(eq("치킨"), any(),
                eq("여자친구 생일"), eq("생일 전까지 감량"), eq(null), eq(null));
    }

    @Test
    @DisplayName("이미 지난 동기는 유효하지 않아 target도 넘기지 않는다")
    void 지난_동기() {
        MotiveSignal signal = MotiveSignal.builder()
                .motiveType(MotiveType.ANNIVERSARY).target("여자친구 생일").paraphrase("생일 전까지 감량")
                .eventDate(LocalDate.now().minusDays(1)).build();
        when(motiveSignalRepository.findFirstByUserIdOrderByIdDesc(1L)).thenReturn(Optional.of(signal));
        stubLlm("치킨 또 먹네", null);

        CoachMessage message = service.generate(user(IntensityLevel.MILD, true), category(), 1);

        assertNull(message.motiveCombo());
        verify(openAiClient).generateCoachMessage(eq("치킨"), any(), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    @DisplayName("target·paraphrase가 빈 문자열이면 내용 없는 동기로 보고 target도 D-day도 넘기지 않는다")
    void 빈문자열_동기() {
        MotiveSignal signal = MotiveSignal.builder()
                .motiveType(MotiveType.ANNIVERSARY).target("").paraphrase("")
                .eventDate(LocalDate.now().plusDays(10)).build();
        when(motiveSignalRepository.findFirstByUserIdOrderByIdDesc(1L)).thenReturn(Optional.of(signal));
        stubLlm("치킨 또 먹네", null);

        CoachMessage message = service.generate(user(IntensityLevel.MILD, true), category(), 1);

        assertNull(message.motiveCombo());
        verify(openAiClient).generateCoachMessage(eq("치킨"), any(), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    @DisplayName("D-day여도 LLM이 빈 콤보 문자열을 주면 motiveCombo를 만들지 않는다")
    void 빈_콤보문자열() {
        MotiveSignal signal = MotiveSignal.builder()
                .motiveType(MotiveType.ANNIVERSARY).target("여자친구 생일").paraphrase("생일 전까지 감량")
                .eventDate(LocalDate.now().plusDays(10)).build();
        when(motiveSignalRepository.findFirstByUserIdOrderByIdDesc(1L)).thenReturn(Optional.of(signal));
        stubLlm("치킨 또 먹네", "");

        CoachMessage message = service.generate(user(IntensityLevel.STRONG, true), category(), 1);

        assertNull(message.motiveCombo());
        assertEquals("치킨 또 먹네", message.text());
    }

    @Test
    @DisplayName("LLM이 실패하면 로그는 이미 저장됐으므로 메시지 없이 null을 반환한다")
    void 생성_실패_graceful() {
        when(motiveSignalRepository.findFirstByUserIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
        when(openAiClient.generateCoachMessage(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("LLM 다운"));

        assertNull(service.generate(user(IntensityLevel.MILD, true), category(), 1));
    }

    @Test
    @DisplayName("이번 주 카운트가 임계 이상이고 빈도 레이어가 켜져 있으면 실제 카운트를 프롬프트에 넘긴다")
    void 빈도_임계도달() {
        when(motiveSignalRepository.findFirstByUserIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
        stubLlm("치킨 이번 주만 4번째냐", null);

        service.generate(user(IntensityLevel.MEDIUM, true), category(), 4);   // threshold 2

        verify(openAiClient).generateCoachMessage(eq("치킨"), any(), eq(null), eq(null), eq(null), eq(4));
    }

    @Test
    @DisplayName("이번 주 카운트가 임계와 같은 첫 도달 시점에도 빈도 컨텍스트를 넘긴다")
    void 빈도_임계경계_포함() {
        when(motiveSignalRepository.findFirstByUserIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
        stubLlm("치킨 이번 주만 2번째냐", null);

        service.generate(user(IntensityLevel.MEDIUM, true), category(), 2);   // threshold 2

        verify(openAiClient).generateCoachMessage(eq("치킨"), any(), eq(null), eq(null), eq(null), eq(2));
    }

    @Test
    @DisplayName("이번 주 카운트가 임계 미만이면 빈도 컨텍스트를 넘기지 않는다")
    void 빈도_임계미만() {
        when(motiveSignalRepository.findFirstByUserIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
        stubLlm("치킨 팩폭", null);

        service.generate(user(IntensityLevel.MEDIUM, true), category(), 1);   // threshold 2

        verify(openAiClient).generateCoachMessage(eq("치킨"), any(), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    @DisplayName("frequencyLayerEnabled=false면 임계 이상이어도 빈도 컨텍스트를 넘기지 않는다")
    void 빈도_레이어_off() {
        when(motiveSignalRepository.findFirstByUserIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
        stubLlm("치킨 팩폭", null);

        User u = User.builder().id(1L).intensityLevel(IntensityLevel.MEDIUM)
                .frequencyLayerEnabled(false).motiveComboEnabled(true).build();
        service.generate(u, category(), 4);   // threshold 2, 임계 이상이지만 레이어 off

        verify(openAiClient).generateCoachMessage(eq("치킨"), any(), eq(null), eq(null), eq(null), eq(null));
    }
}
