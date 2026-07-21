package com.example.motivediet_be.controller;

import com.example.motivediet_be.domain.FavoriteFood;
import com.example.motivediet_be.domain.FoodCategory;
import com.example.motivediet_be.domain.FoodLog;
import com.example.motivediet_be.domain.IntensityLevel;
import com.example.motivediet_be.domain.Role;
import com.example.motivediet_be.domain.User;
import com.example.motivediet_be.repository.FavoriteFoodRepository;
import com.example.motivediet_be.repository.FoodCategoryRepository;
import com.example.motivediet_be.repository.FoodLogRepository;
import com.example.motivediet_be.repository.MotiveSignalRepository;
import com.example.motivediet_be.repository.UserRepository;
import com.example.motivediet_be.service.CoachMessageService;
import com.example.motivediet_be.service.FoodLogService;
import com.example.motivediet_be.service.OpenAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/food-logs 를 HTTP 로 태우는 테스트. 컨트롤러·서비스·프롬프트 조합 규칙까지 실제 코드를 쓰고
 * DB(리포지토리)와 OpenAI 만 대역으로 세운다 — Postman 으로 확인하려던 시나리오를 자동화한 것.
 * ROADMAP Phase 3: 치킨(임계 2) 1회차는 빈도 컨텍스트 없이, 2회차는 실제 카운트가 프롬프트로 나간다.
 */
class FoodLogApiTest {

    private static final long USER_ID = 1L;
    private static final long CATEGORY_ID = 1L;
    private static final long FAVORITE_ID = 1L;
    private static final int THRESHOLD = 2;

    private final FavoriteFoodRepository favoriteFoodRepository = mock(FavoriteFoodRepository.class);
    private final FoodCategoryRepository foodCategoryRepository = mock(FoodCategoryRepository.class);
    private final FoodLogRepository foodLogRepository = mock(FoodLogRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MotiveSignalRepository motiveSignalRepository = mock(MotiveSignalRepository.class);
    private final OpenAiClient openAiClient = mock(OpenAiClient.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new FoodLogController(new FoodLogService(
                    favoriteFoodRepository, foodCategoryRepository, foodLogRepository, userRepository,
                    new CoachMessageService(motiveSignalRepository, openAiClient))))
            .build();

    /** 이번 주 카운트. 테스트가 로깅 직전에 바꿔 끼운다(재스텁하면 Mockito 가 기존 answer 를 실행해버린다). */
    private long weeklyCount;
    private final AtomicLong logIdSeq = new AtomicLong();

    @BeforeEach
    void setUp() {
        FoodCategory chicken = mock(FoodCategory.class);
        when(chicken.getName()).thenReturn("치킨");
        when(chicken.getEmoji()).thenReturn("🍗");
        when(chicken.getWeeklyThreshold()).thenReturn(THRESHOLD);

        when(favoriteFoodRepository.findByIdAndUserId(FAVORITE_ID, USER_ID)).thenReturn(Optional.of(
                FavoriteFood.builder().id(FAVORITE_ID).userId(USER_ID)
                        .foodCategoryId(CATEGORY_ID).slotOrder(0).build()));
        when(foodCategoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(chicken));
        when(foodLogRepository.save(any())).thenAnswer(inv -> {
            FoodLog f = inv.getArgument(0);
            return FoodLog.builder().id(logIdSeq.incrementAndGet()).userId(f.getUserId())
                    .foodCategoryId(f.getFoodCategoryId()).loggedAt(f.getLoggedAt()).build();
        });
        when(foodLogRepository.countByUserIdAndFoodCategoryIdAndLoggedAtGreaterThanEqual(
                anyLong(), anyLong(), any(LocalDateTime.class))).thenAnswer(inv -> weeklyCount);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder()
                .id(USER_ID).name("김석환").email("test@example.com")
                .pictureUrl("https://example.com/p.png").role(Role.ROLE_USER)
                .intensityLevel(IntensityLevel.MEDIUM).consentedAt(LocalDateTime.now())
                .build()));
        when(motiveSignalRepository.findFirstByUserIdOrderByIdDesc(USER_ID)).thenReturn(Optional.empty());

        // LLM 대역: 넘어온 빈도 카운트를 문장에 그대로 박아 프롬프트 전달 여부가 응답에서 보이게 한다.
        when(openAiClient.generateCoachMessage(any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            Integer freq = inv.getArgument(5);
            return new OpenAiClient.CoachResult(
                    freq == null ? "치킨이라니, 오늘도 평화롭게 무너지는구나." : "치킨 이번 주만 벌써 " + freq + "번째다.",
                    null);
        });
    }

    private MvcResult log(long countAfterSave) throws Exception {
        weeklyCount = countAfterSave;
        return mockMvc.perform(post("/api/food-logs")
                        .principal(() -> String.valueOf(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favoriteFoodId\":" + FAVORITE_ID + "}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    @Test
    @DisplayName("치킨 1회차(임계 미만)는 빈도 컨텍스트 없이, 2회차(임계 도달)는 실제 카운트가 프롬프트로 나간다")
    void 치킨_2연타() throws Exception {
        System.out.println("[RESPONSE-1] " + log(1).getResponse().getContentAsString());
        System.out.println("[RESPONSE-2] " + log(2).getResponse().getContentAsString());

        ArgumentCaptor<Integer> freq = ArgumentCaptor.forClass(Integer.class);
        verify(openAiClient, times(2))
                .generateCoachMessage(any(), any(), any(), any(), any(), freq.capture());

        assertNull(freq.getAllValues().get(0), "1회차는 빈도 컨텍스트가 없어야 한다");
        assertEquals(2, freq.getAllValues().get(1), "2회차는 실제 카운트 2가 프롬프트로 나가야 한다");
    }

    @Test
    @DisplayName("응답 스키마: 201 + weeklyCount + coachMessage.text (Phase 3 는 응답 필드를 늘리지 않는다)")
    void 응답_스키마() throws Exception {
        weeklyCount = 2;
        mockMvc.perform(post("/api/food-logs")
                        .principal(() -> String.valueOf(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favoriteFoodId\":" + FAVORITE_ID + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.foodCategory.name").value("치킨"))
                .andExpect(jsonPath("$.foodCategory.emoji").value("🍗"))
                .andExpect(jsonPath("$.weeklyCount").value(2))
                .andExpect(jsonPath("$.coachMessage.toneType").value("FACTBOMB"))
                .andExpect(jsonPath("$.coachMessage.text").value("치킨 이번 주만 벌써 2번째다."))
                .andExpect(jsonPath("$.coachMessage.motiveCombo").doesNotExist());
    }
}
