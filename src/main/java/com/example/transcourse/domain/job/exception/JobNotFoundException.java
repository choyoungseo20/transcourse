package com.example.transcourse.domain.job.exception;

import com.example.transcourse.domain.job.entity.JobType;
import com.example.transcourse.global.apipayload.ErrorStatus;
import com.example.transcourse.global.exception.GeneralException;

public class JobNotFoundException extends GeneralException {

    public JobNotFoundException(Long jobId) {
        super(ErrorStatus.JOB_NOT_FOUND, "존재하지 않는 job: id=" + jobId);
    }

    public JobNotFoundException(Long videoId, JobType type) {
        super(ErrorStatus.JOB_NOT_FOUND, "존재하지 않는 job: videoId=%d, type=%s".formatted(videoId, type));
    }
}
