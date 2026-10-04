package com.example.transcourse.domain.job.experiment;

import com.example.transcourse.domain.job.entity.JobExecutionResult;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkerExecutionRecorder {

    private final WorkerExecutionRepository repository;

    @Transactional
    public Long received(ConsumerRecord<?, ?> record, Long jobId, String memberId, int generationId) {
        WorkerExecution execution = WorkerExecution.received(
                record.topic(), record.partition(), record.offset(), jobId, memberId, generationId);
        return repository.save(execution).getId();
    }

    @Transactional
    public void finished(Long executionId, JobExecutionResult result) {
        repository.findById(executionId).ifPresent(execution -> execution.finish(result));
    }
}
