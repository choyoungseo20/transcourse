package com.example.transcourse.domain.job.entity;

public enum JobStatus {
    PENDING,    // 실행 대기
    RUNNING,    // 실행 중
    SUCCEEDED,  // 성공
    FAILED,     // 실패
    EXHAUSTED,  // 재시도 상한 초과
}
