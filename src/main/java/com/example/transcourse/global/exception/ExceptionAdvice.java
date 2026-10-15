package com.example.transcourse.global.exception;

import com.example.transcourse.global.apipayload.ErrorCode;
import com.example.transcourse.global.apipayload.CommonResponse;
import com.example.transcourse.global.apipayload.ErrorStatus;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class ExceptionAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<Object> handleGeneral(GeneralException ex, WebRequest request) {
        ErrorCode code = ex.getCode();
        CommonResponse<Object> body = CommonResponse.onFailure(code, null);
        return handleExceptionInternal(ex, body, HttpHeaders.EMPTY, code.getHttpStatus(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        CommonResponse<Object> body = CommonResponse.onFailure(ErrorStatus._BAD_REQUEST, null);
        return handleExceptionInternal(ex, body, HttpHeaders.EMPTY, ErrorStatus._BAD_REQUEST.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().stream()
                .forEach(fieldError -> {
                    String fieldName = fieldError.getField();
                    String errorMessage = Optional.ofNullable(fieldError.getDefaultMessage()).orElse("");
                    errors.merge(fieldName, errorMessage,
                            (existingErrorMessage, newErrorMessage) -> existingErrorMessage + ", " + newErrorMessage);
                });

        CommonResponse<Object> body = CommonResponse.onFailure(ErrorStatus._BAD_REQUEST, errors);
        return handleExceptionInternal(ex, body, HttpHeaders.EMPTY, ErrorStatus._BAD_REQUEST.getHttpStatus(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        CommonResponse<Object> body = CommonResponse.onFailure(ErrorStatus._INTERNAL_SERVER_ERROR, null);
        return handleExceptionInternal(ex, body,
                HttpHeaders.EMPTY, ErrorStatus._INTERNAL_SERVER_ERROR.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("서버 예외 발생", ex);
        } else {
            log.warn("요청 오류: {}", ex.getMessage());
        }

        if (!(body instanceof CommonResponse)) {
            ErrorStatus status = statusCode.is5xxServerError()
                    ? ErrorStatus._INTERNAL_SERVER_ERROR : ErrorStatus._BAD_REQUEST;
            body = CommonResponse.onFailure(status, null);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }
}
