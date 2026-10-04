package com.example.transcourse.domain.job.exception;

public class StaleJobAttemptException extends RuntimeException {

    public StaleJobAttemptException(Long jobId, int expectedAttempt, int currentAttempt) {
        super("만료된 시도의 기록 거부: job=%d, 시도 %d ≠ 현재 %d"
                .formatted(jobId, expectedAttempt, currentAttempt));
    }
}
