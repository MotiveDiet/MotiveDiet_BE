package com.example.motivediet_be.controller;

import com.example.motivediet_be.domain.FavoriteFood;
import com.example.motivediet_be.domain.FoodCategory;
import com.example.motivediet_be.domain.FoodLog;
import com.example.motivediet_be.repository.FavoriteFoodRepository;
import com.example.motivediet_be.repository.FoodCategoryRepository;
import com.example.motivediet_be.repository.FoodLogRepository;
import com.example.motivediet_be.repository.MotiveSignalRepository;
import com.example.motivediet_be.service.FavoriteFoodService;
import com.example.motivediet_be.service.HomeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 슬롯 카드의 "이번 주 N회" 강조 판정을 FE 가 하드코딩하지 않도록 weeklyThreshold 를 내려준다.
 * FavoriteFoodResponse 를 만드는 곳이 홈과 즐겨찾기 두 군데라, 한쪽만 채우면 화면마다 스키마가 달라진다 —
 * 그 누락을 잡으려고 두 경로를 같이 태운다.
 */
class WeeklyThresholdApiTest {

    private static final long USER_ID = 1L;
    private static final long CATEGORY_ID = 1L;
    private static final int THRESHOLD = 2;

    private final FavoriteFoodRepository favoriteFoodRepository = mock(FavoriteFoodRepository.class);
    private final FoodCategoryRepository foodCategoryRepository = mock(FoodCategoryRepository.class);
    private final FoodLogRepository foodLogRepository = mock(FoodLogRepository.class);
    private final MotiveSignalRepository motiveSignalRepository = mock(MotiveSignalRepository.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(
                    new HomeController(new HomeService(motiveSignalRepository, favoriteFoodRepository,
                            foodCategoryRepository, foodLogRepository)),
                    new FavoriteFoodController(new FavoriteFoodService(favoriteFoodRepository,
                            foodCategoryRepository, foodLogRepository)))
            .build();

    @BeforeEach
    void setUp() {
        FoodCategory chicken = mock(FoodCategory.class);
        when(chicken.getId()).thenReturn(CATEGORY_ID);
        when(chicken.getName()).thenReturn("치킨");
        when(chicken.getEmoji()).thenReturn("🍗");
        when(chicken.getWeeklyThreshold()).thenReturn(THRESHOLD);

        when(foodCategoryRepository.findAll()).thenReturn(List.of(chicken));
        when(foodCategoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(chicken));
        when(motiveSignalRepository.findFirstByUserIdOrderByIdDesc(USER_ID)).thenReturn(Optional.empty());
        when(foodLogRepository.findLogDates(USER_ID)).thenReturn(List.of());
        when(foodLogRepository.countByUserIdAndFoodCategoryIdAndLoggedAtGreaterThanEqual(
                anyLong(), anyLong(), any(LocalDateTime.class))).thenReturn(3L);
    }

    @Test
    @DisplayName("GET /api/home: 즐겨찾기마다 이번 주 카운트와 카테고리 임계값이 함께 나간다")
    void 홈_응답() throws Exception {
        FavoriteFood favorite = FavoriteFood.builder()
                .id(9L).userId(USER_ID).foodCategoryId(CATEGORY_ID).slotOrder(0).build();
        when(favoriteFoodRepository.findByUserIdOrderBySlotOrder(USER_ID)).thenReturn(List.of(favorite));
        // 이번 주 치킨 3회 — 임계 2를 넘겨 FE 가 강조하는 상태
        when(foodLogRepository.findByUserIdAndLoggedAtGreaterThanEqual(anyLong(), any(LocalDateTime.class)))
                .thenReturn(List.of(foodLog(), foodLog(), foodLog()));

        mockMvc.perform(get("/api/home").principal(() -> String.valueOf(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteFoods[0].name").value("치킨"))
                .andExpect(jsonPath("$.favoriteFoods[0].weeklyCount").value(3))
                .andExpect(jsonPath("$.favoriteFoods[0].weeklyThreshold").value(THRESHOLD));
    }

    @Test
    @DisplayName("POST /api/favorite-foods: 슬롯 추가 응답도 같은 스키마로 임계값을 실어 보낸다")
    void 즐겨찾기_추가_응답() throws Exception {
        when(favoriteFoodRepository.findByUserIdOrderBySlotOrder(USER_ID)).thenReturn(List.of());
        when(favoriteFoodRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/favorite-foods")
                        .principal(() -> String.valueOf(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"foodCategoryId\":" + CATEGORY_ID + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weeklyCount").value(3))
                .andExpect(jsonPath("$.weeklyThreshold").value(THRESHOLD));
    }

    private FoodLog foodLog() {
        return FoodLog.builder().userId(USER_ID).foodCategoryId(CATEGORY_ID)
                .loggedAt(LocalDateTime.now()).build();
    }
}
