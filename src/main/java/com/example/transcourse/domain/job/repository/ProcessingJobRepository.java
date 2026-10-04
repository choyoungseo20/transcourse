package com.example.transcourse.domain.job.repository;

import com.example.transcourse.domain.job.entity.JobStatus;
import com.example.transcourse.domain.job.entity.JobType;
import com.example.transcourse.domain.job.entity.ProcessingJob;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {

    List<ProcessingJob> findByVideoId(Long videoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProcessingJob> findWithLockById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProcessingJob> findWithLockByVideoIdAndType(Long videoId, JobType type);

    List<ProcessingJob> findByStatus(JobStatus status);

    List<ProcessingJob> findByStatusAndCreatedAtBefore(JobStatus status, LocalDateTime threshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ProcessingJob> findWithLockByStatusAndStartedAtBefore(JobStatus status, LocalDateTime threshold);
}
