package com.example.motivediet_be.controller;

import com.example.motivediet_be.jwt.TokenProvider;
import com.example.motivediet_be.repository.UserRepository;
import com.example.motivediet_be.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerStateTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private TokenProvider tokenProvider;

    // 전역 ConsentInterceptor 가 이 web 슬라이스에도 로드되므로 그 의존성만 채워준다(테스트 대상 아님).
    @MockitoBean
    private UserRepository userRepository;

    @Test
    @WithMockUser
    @DisplayName("state 쿠키 없이 콜백을 치면 400이고, 인가 코드 교환을 시도하지 않는다")
    void 쿠키_없으면_거부() throws Exception {
        mockMvc.perform(get("/api/oauth2/callback/google")
                        .param("code", "attacker-code")
                        .param("state", "attacker-state"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).getGoogleAccessToken(anyString());
    }

    @Test
    @WithMockUser
    @DisplayName("쿠키의 state와 파라미터 state가 다르면 400이고, 인가 코드 교환을 시도하지 않는다")
    void state_불일치면_거부() throws Exception {
        mockMvc.perform(get("/api/oauth2/callback/google")
                        .param("code", "attacker-code")
                        .param("state", "attacker-state")
                        .cookie(new Cookie("OAUTH2_STATE", "victim-state")))
                .andExpect(status().isBadRequest());

        verify(authService, never()).getGoogleAccessToken(anyString());
    }
}
