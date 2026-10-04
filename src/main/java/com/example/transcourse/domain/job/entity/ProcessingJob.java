package com.example.transcourse.domain.job.entity;

import com.example.transcourse.domain.job.exception.InvalidJobTransitionException;
import com.example.transcourse.domain.job.exception.StaleJobAttemptException;
import com.example.transcourse.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "processing_job",
        uniqueConstraints = @UniqueConstraint(columnNames = {"video_id", "type"}),
        indexes = @Index(name = "idx_processing_job_status", columnList = "status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingJob extends BaseEntity {

    private static final int MAX_ATTEMPT_COUNT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false)
    private Long videoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(nullable = false)
    private int attemptCount;

    // 시도별 이력이 아닌 최근 시도의 값
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String lastFailureReason;

    private ProcessingJob(Long videoId, JobType type) {
        this.videoId = videoId;
        this.type = type;
        this.status = JobStatus.PENDING;
        this.attemptCount = 0;
    }

    public static ProcessingJob create(Long videoId, JobType type) {
        return new ProcessingJob(videoId, type);
    }

    // RUNNING에서의 start는 at-least-once 재전달을 새 시도로 선점하는 경우
    // 먼저 돌던 시도는 중단 없이 계속 실행
    // 먼저 돌던 시도의 결과는 시도 번호 불일치로 거부
    public void start() {
        ensureStatusIn(JobStatus.PENDING, JobStatus.FAILED, JobStatus.RUNNING);
        this.attemptCount++;
        this.status = JobStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.finishedAt = null;
    }

    public void succeed(int expectedAttempt) {
        ensureStatusIn(JobStatus.RUNNING);
        ensureCurrentAttempt(expectedAttempt);
        this.status = JobStatus.SUCCEEDED;
        this.finishedAt = LocalDateTime.now();
    }

    public void fail(String reason, int expectedAttempt) {
        ensureStatusIn(JobStatus.RUNNING);
        ensureCurrentAttempt(expectedAttempt);
        this.status = attemptCount >= MAX_ATTEMPT_COUNT ? JobStatus.EXHAUSTED : JobStatus.FAILED;
        this.lastFailureReason = reason;
        this.finishedAt = LocalDateTime.now();
    }

    public boolean isRetryable() {
        return status == JobStatus.FAILED || status == JobStatus.EXHAUSTED;
    }

    public void resetForManualRetry() {
        ensureStatusIn(JobStatus.EXHAUSTED, JobStatus.FAILED);
        this.status = JobStatus.PENDING;
        this.startedAt = null;
        this.finishedAt = null;
        this.lastFailureReason = null;
    }

    private void ensureCurrentAttempt(int expectedAttempt) {
        if (this.attemptCount != expectedAttempt) {
            throw new StaleJobAttemptException(id, expectedAttempt, attemptCount);
        }
    }

    private void ensureStatusIn(JobStatus... allowed) {
        if (!List.of(allowed).contains(this.status)) {
            throw new InvalidJobTransitionException(id, status);
        }
    }
}
