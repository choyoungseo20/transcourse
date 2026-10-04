package com.example.transcourse.global.apipayload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements ErrorCode {

    _INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러, 관리자에게 문의 바랍니다."),
    _BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    _UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    _FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),

    VIDEO_EMPTY_FILE(HttpStatus.BAD_REQUEST, "VIDEO4001", "업로드할 영상 파일이 비어 있습니다."),
    VIDEO_NOT_FOUND(HttpStatus.NOT_FOUND, "VIDEO4002", "존재하지 않는 영상입니다."),
    VIDEO_ARTIFACT_NOT_READY(HttpStatus.CONFLICT, "VIDEO4003", "산출물이 아직 준비되지 않았습니다. 처리 상태를 확인해 주세요."),

    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "JOB4001", "존재하지 않는 처리 작업입니다."),
    JOB_NOT_RETRYABLE(HttpStatus.CONFLICT, "JOB4002", "실패 상태의 작업만 재시도할 수 있습니다."),

    STORAGE_SAVE_FAILURE(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE5001", "파일 저장에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    STORAGE_READ_FAILURE(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE5002", "파일 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
