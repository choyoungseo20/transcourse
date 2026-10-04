package com.example.transcourse.domain.job.entity;

public enum JobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    EXHAUSTED,  // 재시도 상한 초과
}
