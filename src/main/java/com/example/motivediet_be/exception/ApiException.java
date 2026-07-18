package com.example.motivediet_be.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 계약에 명시된 에러 코드({"error": "..."})를 그대로 내려주기 위한 예외.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }
}
