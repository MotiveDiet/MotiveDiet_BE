package com.example.motivediet_be.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 코칭 API(POST /api/food-logs)에 동의 게이트를 건다. Phase 3 펀치라인도 같은 엔드포인트라 여기서 커버된다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ConsentInterceptor consentInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(consentInterceptor)
                .addPathPatterns("/api/food-logs", "/api/food-logs/**");
    }
}
