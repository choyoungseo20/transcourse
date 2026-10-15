package com.example.transcourse.domain.job.exception;

import com.example.transcourse.domain.job.entity.JobStatus;
import com.example.transcourse.global.apipayload.ErrorStatus;
import com.example.transcourse.global.exception.GeneralException;

public class JobNotRetryableException extends GeneralException {

    public JobNotRetryableException(Long jobId, JobStatus status) {
        super(ErrorStatus.JOB_NOT_RETRYABLE, "재시도 불가 상태: job=%d, status=%s".formatted(jobId, status));
    }
}
