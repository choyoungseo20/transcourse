package com.example.videopipeline.domain.job.entity;

import com.example.videopipeline.domain.job.service.JobExecutionResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 증설 실험용 — 레코드(topic, partition, offset)마다 어느 컨슈머가 언제 무엇을 했는지 남긴다.
// 같은 (partition, offset)이 두 번 이상 나타나면 재전달이다.
@Entity
@Table(
        name = "worker_execution",
        indexes = @Index(name = "idx_worker_execution_record", columnList = "partition_no, record_offset"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkerExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "partition_no", nullable = false)
    private int partition;

    @Column(name = "record_offset", nullable = false)
    private long offset;

    @Column(nullable = false)
    private Long jobId;

    @Column(nullable = false)
    private String memberId;

    @Column(nullable = false)
    private int generationId;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    private JobExecutionResult result;

    public static WorkerExecution received(
            String topic, int partition, long offset, Long jobId, String memberId, int generationId) {
        WorkerExecution execution = new WorkerExecution();
        execution.topic = topic;
        execution.partition = partition;
        execution.offset = offset;
        execution.jobId = jobId;
        execution.memberId = memberId;
        execution.generationId = generationId;
        execution.receivedAt = LocalDateTime.now();
        return execution;
    }

    public void finish(JobExecutionResult result) {
        this.result = result;
        this.finishedAt = LocalDateTime.now();
    }
}
