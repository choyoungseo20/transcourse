package com.example.transcourse.domain.job.entity;

import java.util.List;

public enum OverallStatus {
    PROCESSING,
    COMPLETED,
    FAILED;

    public static OverallStatus from(List<ProcessingJob> jobs) {
        if (jobs.stream().allMatch(j -> j.getStatus() == JobStatus.SUCCEEDED)) {
            return COMPLETED;
        }
        if (jobs.stream().anyMatch(j -> j.getStatus() == JobStatus.EXHAUSTED)) {
            return FAILED;
        }
        return PROCESSING;
    }
}
