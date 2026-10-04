package com.example.transcourse.global.apipayload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"code", "message", "result"})
public record CommonResponse<T>(
    String code,
    String message,
    @JsonInclude(JsonInclude.Include.NON_NULL) T result) {

    public static <T> CommonResponse<T> onSuccess(T result) {
        return onSuccess(SuccessStatus.OK, result);
    }

    public static <T> CommonResponse<T> onSuccess(SuccessCode code, T result) {
        return new CommonResponse<>(code.getCode(), code.getMessage(), result);
    }

    public static <T> CommonResponse<T> onFailure(ErrorCode code, T result) {
        return new CommonResponse<>(code.getCode(), code.getMessage(), result);
    }
}
