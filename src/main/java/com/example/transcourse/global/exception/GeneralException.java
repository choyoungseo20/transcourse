package com.example.transcourse.global.exception;

import com.example.transcourse.global.apipayload.ErrorCode;
import lombok.Getter;

// code에 싣는 클라이언트용 메시지
// getMessage()에 싣는 로그용 상세
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
