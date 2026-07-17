package com.example.motivediet_be.controller;

import com.example.motivediet_be.dto.TokenDto;
import com.example.motivediet_be.jwt.TokenProvider;
import com.example.motivediet_be.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerCallbackTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private TokenProvider tokenProvider;

    @Test
    @WithMockUser
    @DisplayName("콜백이 성공하면 JSON 본문이 아니라 앱 커스텀 스킴으로 302 리다이렉트한다")
    void 콜백_성공시_앱스킴으로_리다이렉트() throws Exception {
        when(authService.getGoogleAccessToken("valid-code")).thenReturn("google-token");
        when(authService.loginOrSignUp("google-token"))
                .thenReturn(TokenDto.builder().accessToken("jwt-token").build());

        mockMvc.perform(get("/api/oauth2/callback/google")
                        .param("code", "valid-code")
                        .param("state", "same-state")
                        .cookie(new Cookie("OAUTH2_STATE", "same-state")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("motivediet://auth?token=jwt-token"));
    }
}
