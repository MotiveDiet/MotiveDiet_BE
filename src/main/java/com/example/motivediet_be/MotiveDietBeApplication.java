package com.example.motivediet_be;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class MotiveDietBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MotiveDietBeApplication.class, args);
    }

    // 한국 단일 리전 서비스. 서버 기본 TZ(Railway=UTC)로 두면 스트릭/주간 카운트가
    // 자정 근처에서 하루 어긋난다. now() 계열이 전부 KST 벽시계를 쓰도록 한 곳에서 고정한다.
    @PostConstruct
    void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

}
