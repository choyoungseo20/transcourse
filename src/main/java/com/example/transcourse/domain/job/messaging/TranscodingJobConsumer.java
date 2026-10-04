package com.example.transcourse.domain.job.messaging;

import com.example.transcourse.domain.job.entity.JobExecutionResult;
import com.example.transcourse.domain.job.entity.JobType;
import com.example.transcourse.domain.job.entity.ProcessingJob;
import com.example.transcourse.domain.job.exception.InvalidJobTransitionException;
import com.example.transcourse.domain.job.exception.JobNotFoundException;
import com.example.transcourse.domain.job.experiment.WorkerExecutionRecorder;
import com.example.transcourse.domain.job.service.JobService;
import com.example.transcourse.domain.job.service.JobWorker;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;

// max.poll.interval.ms를 넘기는 인코딩의 워커 스레드 실행
// 워커 스레드에서도 호출 가능한 컨테이너 API로의 pause·resume 요청
// 리밸런스 후 재할당된 파티션에 대한 컨테이너의 pause 재적용
// 재할당 파티션의 재전달 레코드는 진행 중 작업 종료 뒤 도착
@Slf4j
@Component
public class TranscodingJobConsumer {

    private final JobService jobService;
    private final JobWorker jobWorker;
    private final WorkerExecutionRecorder recorder;
    private final KafkaListenerEndpointRegistry registry;
    private final Executor transcodingExecutor;

    public TranscodingJobConsumer(
            JobService jobService,
            JobWorker jobWorker,
            WorkerExecutionRecorder recorder,
            KafkaListenerEndpointRegistry registry,
            @Qualifier("transcodingExecutor") Executor transcodingExecutor) {
        this.jobService = jobService;
        this.jobWorker = jobWorker;
        this.recorder = recorder;
        this.registry = registry;
        this.transcodingExecutor = transcodingExecutor;
    }

    @RetryableTopic(
            attempts = "${app.kafka.job-attempts}",
            backOff = @BackOff(delayString = "${app.kafka.retry-delay-ms}", multiplier = 2),
            retryTopicSuffix = "-transcoding-retry",
            dltTopicSuffix = "-transcoding-dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            numPartitions = "${app.kafka.jobs-partitions}",
            replicationFactor = "1",
            listenerContainerFactory = "transcodingContainerFactory")
    @KafkaListener(
            id = "job-transcoding",
            topics = "${app.kafka.jobs-topic}",
            containerFactory = "transcodingContainerFactory")
    public CompletableFuture<Void> onTranscodingRequested(ConsumerRecord<String, String> record, Consumer<?, ?> consumer) {
        if (!JobRequest.targets(record, JobType.TRANSCODING)) {
            return CompletableFuture.completedFuture(null);
        }
        Long videoId = JobRequest.videoId(record);
        ProcessingJob job;
        try {
            job = jobService.getJob(videoId, JobType.TRANSCODING);
        } catch (JobNotFoundException e) {
            log.warn("존재하지 않는 job의 실행 요청을 버림: videoId={}, type={}", videoId, JobType.TRANSCODING);
            return CompletableFuture.completedFuture(null);
        }
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        ConsumerGroupMetadata group = consumer.groupMetadata();
        Long executionId = recorder.received(record, job.getId(), group.memberId(), group.generationId());

        MessageListenerContainer container = containerOf(partition);
        container.pausePartition(partition);
        CompletableFuture<Void> done = new CompletableFuture<>();
        transcodingExecutor.execute(() -> {
            try {
                JobExecutionResult result = run(job);
                recorder.finished(executionId, result);
                if (result == JobExecutionResult.FAILED) {
                    done.completeExceptionally(new TranscodingFailedException(job.getId()));
                } else {
                    done.complete(null);
                }
            } catch (RuntimeException e) {
                done.completeExceptionally(e);
            } finally {
                container.resumePartition(partition);
            }
        });
        return done;
    }

    @DltHandler
    public void onExhausted(ConsumerRecord<String, String> record) {
        Long videoId = JobRequest.videoId(record);
        log.warn("재시도 소진: videoId={}, type={}", videoId, JobType.TRANSCODING);
        try {
            jobService.markExhausted(videoId, JobType.TRANSCODING);
        } catch (JobNotFoundException | InvalidJobTransitionException e) {
            log.warn("소진 처리 생략: videoId={}, 사유={}", videoId, e.getMessage());
        }
    }

    private JobExecutionResult run(ProcessingJob job) {
        try {
            return jobWorker.execute(job);
        } catch (RuntimeException e) {
            log.error("job 실행 중 예상 밖 예외: jobId={}", job.getId(), e);
            return JobExecutionResult.FAILED;
        }
    }

    // 재시도 토픽마다 따로 있는 컨테이너 중 파티션 보유 컨테이너 탐색
    private MessageListenerContainer containerOf(TopicPartition partition) {
        return registry.getAllListenerContainers().stream()
                .filter(c -> c.getAssignedPartitions() != null && c.getAssignedPartitions().contains(partition))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("파티션을 가진 컨테이너 없음: " + partition));
    }

    static class TranscodingFailedException extends RuntimeException {
        TranscodingFailedException(Long jobId) {
            super("트랜스코딩 실패: jobId=" + jobId);
        }
    }
}
