package com.example.transcourse.global.exception;

import com.example.transcourse.global.apipayload.ErrorCode;
import lombok.Getter;

// 에러 컨텍스트 운반체 — 클라이언트용 메시지는 code에, 로그용 상세(detail)는 getMessage()에 싣는다
@Getter
public class GeneralException extends RuntimeException {

    private final ErrorCode code;

    public GeneralException(ErrorCode code) {
        super(code.getMessage());
        this.code = code;
    }

    public GeneralException(ErrorCode code, Throwable cause) {
        super(code.getMessage(), cause);
        this.code = code;
    }

    public GeneralException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public GeneralException(ErrorCode code, String detail, Throwable cause) {
        super(detail, cause);
        this.code = code;
    }
}
