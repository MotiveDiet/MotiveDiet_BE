package com.example.motivediet_be.config;

import com.example.motivediet_be.domain.User;
import com.example.motivediet_be.exception.ApiException;
import com.example.motivediet_be.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.Principal;

/**
 * 팩폭/펀치라인 API는 opt-in 동의(User.consentedAt)가 있어야 한다.
 * 미동의면 403 CONSENT_REQUIRED (API.md 5절). 인증은 이미 SecurityFilterChain이 보장한다.
 */
@Component
@RequiredArgsConstructor
public class ConsentInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Principal principal = request.getUserPrincipal();
        User user = userRepository.findById(Long.parseLong(principal.getName()))
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));
        if (user.getConsentedAt() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CONSENT_REQUIRED");
        }
        return true;
    }
}
