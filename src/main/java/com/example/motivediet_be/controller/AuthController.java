package com.example.motivediet_be.controller;

import com.example.motivediet_be.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@RequestMapping("/api/oauth2")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private static final String OAUTH_STATE_COOKIE_NAME = "OAUTH2_STATE";
    private static final int OAUTH_STATE_COOKIE_MAX_AGE = 300;
    private static final String APP_CALLBACK_URL = "motivediet://auth";

    private final AuthService authService;
    private final SecureRandom secureRandom = new SecureRandom();

    @GetMapping("/login/google")
    public RedirectView googleLogin(HttpServletRequest request, HttpServletResponse response) {
        String state = createState();
        addStateCookie(request, response, state, OAUTH_STATE_COOKIE_MAX_AGE);

        return new RedirectView(authService.getGoogleLoginUrl(state));
    }

    @GetMapping("/callback/google")
    public RedirectView googleCallback(@RequestParam("code") String code,
                                       @RequestParam("state") String state,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        validateState(request, state);
        addStateCookie(request, response, "", 0);

        String googleAccessToken = authService.getGoogleAccessToken(code); // google 토큰 받기
        String accessToken = authService.loginOrSignUp(googleAccessToken).getAccessToken();

        // iOS는 ASWebAuthenticationSession(웹뷰)으로 이 흐름을 타므로 JSON 본문을 앱이 받을 수 없다.
        // 커스텀 URL 스킴으로 리다이렉트해야 세션 콜백에서 토큰을 꺼낼 수 있다
        return new RedirectView(UriComponentsBuilder.fromUriString(APP_CALLBACK_URL)
                .queryParam("token", accessToken)
                .toUriString());
    }

    private String createState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void addStateCookie(HttpServletRequest request, HttpServletResponse response, String state, int maxAge) {
        // HTTPS로 들어온 요청에만 Secure를 붙인다. 로컬 http 개발을 깨뜨리지 않기 위함이며,
        // 프록시 뒤(Railway)에서 isSecure()가 동작하려면 server.forward-headers-strategy 설정이 필요하다
        response.addHeader("Set-Cookie", OAUTH_STATE_COOKIE_NAME + "=" + state
                + "; Path=/; Max-Age=" + maxAge + "; HttpOnly; SameSite=Lax"
                + (request.isSecure() ? "; Secure" : ""));
    }

    private void validateState(HttpServletRequest request, String state) {
        String savedState = getCookieValue(request, OAUTH_STATE_COOKIE_NAME);

        if (savedState == null || !constantTimeEquals(savedState, state)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 OAuth state 입니다.");
        }
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

}
