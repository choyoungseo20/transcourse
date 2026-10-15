package com.example.transcourse.domain.job.exception;

import com.example.transcourse.domain.job.entity.JobStatus;

public class InvalidJobTransitionException extends RuntimeException {

    public InvalidJobTransitionException(Long jobId, JobStatus status) {
        super("허용되지 않은 상태 전이: job=%d, %s에서 전이 불가".formatted(jobId, status));
    }
}
