package com.example.motivediet_be.controller;

import com.example.motivediet_be.config.ConsentInterceptor;
import com.example.motivediet_be.config.WebConfig;
import com.example.motivediet_be.domain.Role;
import com.example.motivediet_be.domain.User;
import com.example.motivediet_be.dto.UserMeResponse;
import com.example.motivediet_be.repository.UserRepository;
import com.example.motivediet_be.service.OnboardingService;
import com.example.motivediet_be.service.UserSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 미동의 사용자도 GET /api/users/me 는 200 이어야 한다 — 여기서 동의 여부를 받아 동의화면으로 보내므로,
 * 이 경로가 동의 게이트에 걸리면 앱이 영원히 동의화면에 못 들어간다(닭-달걀).
 *
 * standaloneSetup 인 UserMeApiTest 로는 못 잡는 계약이라 실제 WebConfig·ConsentInterceptor 를 올린다.
 * 시큐리티 필터체인은 끈다(addFilters=false) — 여기서 볼 것은 인터셉터 경로 패턴 하나다.
 */
@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, ConsentInterceptor.class})
class UserMeConsentGateTest {

    private static final long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private UserSettingsService userSettingsService;
    @MockitoBean
    private OnboardingService onboardingService;

    @Test
    @DisplayName("consentedAt 이 null 인 사용자도 200 — 동의 게이트는 이 경로에 걸리지 않는다")
    void 미동의_200() throws Exception {
        User notConsented = User.builder()
                .id(USER_ID).name("김석환").email("test@example.com")
                .pictureUrl("https://example.com/p.png").role(Role.ROLE_USER)
                .build();
        // 인터셉터가 걸려 있다면 여기서 조회한 consentedAt=null 로 403 CONSENT_REQUIRED 를 던진다.
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(notConsented));
        when(userSettingsService.getMe(USER_ID)).thenReturn(UserMeResponse.from(notConsented));

        mockMvc.perform(get("/api/users/me").principal(() -> String.valueOf(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboarded").value(false));
    }
}
