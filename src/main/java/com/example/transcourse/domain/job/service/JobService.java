package com.example.transcourse.domain.job.service;

import com.example.transcourse.domain.job.entity.JobStatus;
import com.example.transcourse.domain.job.entity.JobType;
import com.example.transcourse.domain.job.entity.ProcessingJob;
import com.example.transcourse.domain.job.exception.JobNotFoundException;
import com.example.transcourse.domain.job.exception.JobNotRetryableException;
import com.example.transcourse.domain.job.repository.ProcessingJobRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 상태 전이를 각각 짧은 트랜잭션으로 커밋해, 처리 도중에도 현재 상태가 조회에 보이게 한다
@Service
@RequiredArgsConstructor
public class JobService {

    private static final String TIMED_OUT_FAILURE_REASON = "실행 타임아웃 초과 — 서버 중단으로 유실된 시도로 판정";

    private final ProcessingJobRepository jobRepository;

    @Transactional(readOnly = true)
    public List<ProcessingJob> findAllFor(Long videoId) {
        return jobRepository.findByVideoId(videoId);
    }

    @Transactional(readOnly = true)
    public ProcessingJob getJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }

    @Transactional
    public void createAllFor(Long videoId) {
        Arrays.stream(JobType.values())
                .map(type -> ProcessingJob.create(videoId, type))
                .forEach(jobRepository::save);
    }

    @Transactional
    public int markStarted(Long jobId) {
        ProcessingJob job = findJob(jobId);
        job.start();
        return job.getAttemptCount();
    }

    @Transactional
    public void markSucceeded(Long jobId, int attempt) {
        findJob(jobId).succeed(attempt);
    }

    @Transactional
    public void markFailed(Long jobId, String reason, int attempt) {
        findJob(jobId).fail(reason, attempt);
    }

    @Transactional
    public ProcessingJob resetForRetry(Long videoId, JobType type) {
        ProcessingJob job = jobRepository.findWithLockByVideoIdAndType(videoId, type)
                .orElseThrow(() -> new JobNotFoundException(videoId, type));
        if (!job.isRetryable()) {
            throw new JobNotRetryableException(job.getId(), job.getStatus());
        }
        job.resetForManualRetry();
        return job;
    }

    // 폴러 전용
    @Transactional
    public List<ProcessingJob> failTimedOut(LocalDateTime startedBefore) {
        List<ProcessingJob> zombies =
                jobRepository.findWithLockByStatusAndStartedAtBefore(JobStatus.RUNNING, startedBefore);
        zombies.forEach(job -> job.fail(TIMED_OUT_FAILURE_REASON, job.getAttemptCount()));
        return zombies;
    }

    // 폴러 전용
    @Transactional(readOnly = true)
    public List<ProcessingJob> findRecoverable(LocalDateTime pendingCreatedBefore) {
        List<ProcessingJob> recoverable =
                jobRepository.findByStatusAndCreatedAtBefore(JobStatus.PENDING, pendingCreatedBefore);
        recoverable.addAll(jobRepository.findByStatus(JobStatus.FAILED));
        return recoverable;
    }

    private ProcessingJob findJob(Long jobId) {
        return jobRepository.findWithLockById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }
}
