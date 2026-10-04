package com.example.videopipeline.domain.job.entity;

public enum JobExecutionResult {
    SKIPPED,          // 실행 가능 상태가 아니어서 처리하지 않은 경우
    SUCCEEDED,
    FAILED,
    RESULT_REJECTED,  // 처리를 마쳤으나 만료된 시도라 결과 기록이 거부된 경우
}
