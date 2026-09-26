package com.example.videopipeline.domain.job.service;

public enum JobExecutionResult {
    SKIPPED,          // 실행 가능 상태가 아니어서 처리하지 않음
    SUCCEEDED,
    FAILED,
    RESULT_REJECTED,  // 처리는 끝났지만 만료된 시도라 결과 기록이 거부됨 — 버려진 연산
}
