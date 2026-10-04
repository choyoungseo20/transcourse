package com.example.transcourse.global.apipayload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuccessStatus implements SuccessCode {

    OK(HttpStatus.OK, "COMMON200", "성공적으로 요청을 수행하였습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
