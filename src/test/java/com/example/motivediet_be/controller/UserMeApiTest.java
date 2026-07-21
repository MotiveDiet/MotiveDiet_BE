package com.example.motivediet_be.controller;

import com.example.motivediet_be.domain.Role;
import com.example.motivediet_be.domain.User;
import com.example.motivediet_be.repository.UserRepository;
import com.example.motivediet_be.service.OnboardingService;
import com.example.motivediet_be.service.UserSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/users/me 를 HTTP 로 태우는 테스트. 직렬화 계약(날짜 포맷·null 유지)까지 실제 코드로 확인한다.
 * 이 응답으로 FE 가 동의화면·온보딩·홈 중 어디로 갈지 정하므로 필드 하나만 어긋나도 진입 분기가 깨진다.
 * 컨트롤러·서비스·직렬화만 태운다(standaloneSetup) — 인터셉터·시큐리티 필터체인은 범위 밖이다.
 */
class UserMeApiTest {

    private static final long USER_ID = 1L;

    private final UserRepository userRepository = mock(UserRepository.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new UserController(
                    mock(OnboardingService.class), new UserSettingsService(userRepository)))
            .build();

    private void givenUser(LocalDateTime consentedAt, Double goalWeight, LocalDate goalDate) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder()
                .id(USER_ID).name("김석환").email("test@example.com")
                .pictureUrl("https://example.com/p.png").role(Role.ROLE_USER)
                .consentedAt(consentedAt).goalWeight(goalWeight).goalDate(goalDate)
                .build()));
    }

    @Test
    @DisplayName("동의·온보딩을 마친 사용자: 200 + 목표값 그대로 + onboarded=true")
    void 온보딩_완료() throws Exception {
        givenUser(LocalDateTime.of(2026, 7, 12, 9, 41), 68.0, LocalDate.of(2026, 9, 1));

        mockMvc.perform(get("/api/users/me").principal(() -> String.valueOf(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentedAt").value("2026-07-12T09:41:00"))
                .andExpect(jsonPath("$.goalWeight").value(68.0))
                .andExpect(jsonPath("$.goalDate").value("2026-09-01"))
                .andExpect(jsonPath("$.onboarded").value(true));
    }

    @Test
    // 동의 게이트가 이 경로에 걸리는지는 여기서 못 잡는다 — standaloneSetup 에는 WebConfig 의
    // ConsentInterceptor 가 등록되지 않는다. 그 계약은 WebConfig 의 경로 패턴이 진실의 원천이다.
    @DisplayName("동의·목표가 다 비어 있어도 필드가 null 로 살아서 나가고 onboarded=false")
    void 빈_사용자() throws Exception {
        givenUser(null, null, null);

        mockMvc.perform(get("/api/users/me").principal(() -> String.valueOf(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentedAt").value(nullValue()))
                .andExpect(jsonPath("$.goalWeight").value(nullValue()))
                .andExpect(jsonPath("$.goalDate").value(nullValue()))
                .andExpect(jsonPath("$.onboarded").value(false));
    }

    @Test
    @DisplayName("목표 날짜만 빠져도 onboarded=false — 온보딩 화면으로 되돌린다")
    void 목표날짜_누락() throws Exception {
        givenUser(LocalDateTime.of(2026, 7, 12, 9, 41), 68.0, null);

        mockMvc.perform(get("/api/users/me").principal(() -> String.valueOf(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalWeight").value(68.0))
                .andExpect(jsonPath("$.onboarded").value(false));
    }
}
