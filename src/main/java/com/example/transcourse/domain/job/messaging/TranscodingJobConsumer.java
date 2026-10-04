package com.example.transcourse.domain.job.messaging;

import com.example.transcourse.domain.job.entity.JobExecutionResult;
import com.example.transcourse.domain.job.entity.JobType;
import com.example.transcourse.domain.job.entity.ProcessingJob;
import com.example.transcourse.domain.job.exception.InvalidJobTransitionException;
import com.example.transcourse.domain.job.exception.JobNotFoundException;
import com.example.transcourse.domain.job.experiment.WorkerExecutionRecorder;
import com.example.transcourse.domain.job.service.JobService;
import com.example.transcourse.domain.job.service.JobWorker;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

// max.poll.interval.ms를 넘기는 인코딩의 워커 스레드 실행
// 워커 스레드에서도 호출 가능한 컨테이너 API로의 pause·resume 요청
// 리밸런스 후 재할당된 파티션에 대한 컨테이너의 pause 재적용
// 재할당 파티션의 재전달 레코드는 진행 중 작업 종료 뒤 도착
@Slf4j
@Component
public class TranscodingJobConsumer {

    private static final String ATTEMPT_HEADER = "transcoding-attempt";
    private static final String RETRY_AT_HEADER = "transcoding-retry-at";

    private final JobService jobService;
    private final JobWorker jobWorker;
    private final WorkerExecutionRecorder recorder;
    private final KafkaListenerEndpointRegistry registry;
    private final Executor transcodingExecutor;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String retryTopic;
    private final int maxAttempts;
    private final long retryDelayMs;

    public TranscodingJobConsumer(
            JobService jobService,
            JobWorker jobWorker,
            WorkerExecutionRecorder recorder,
            KafkaListenerEndpointRegistry registry,
            @Qualifier("transcodingExecutor") Executor transcodingExecutor,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.transcoding-retry-topic}") String retryTopic,
            @Value("${app.kafka.job-attempts}") int maxAttempts,
            @Value("${app.kafka.retry-delay-ms}") long retryDelayMs) {
        this.jobService = jobService;
        this.jobWorker = jobWorker;
        this.recorder = recorder;
        this.registry = registry;
        this.transcodingExecutor = transcodingExecutor;
        this.kafkaTemplate = kafkaTemplate;
        this.retryTopic = retryTopic;
        this.maxAttempts = maxAttempts;
        this.retryDelayMs = retryDelayMs;
    }

    @KafkaListener(
            id = "job-transcoding",
            topics = "${app.kafka.jobs-topic}",
            containerFactory = "transcodingContainerFactory")
    public void onTranscodingRequested(ConsumerRecord<String, String> record, Acknowledgment ack, Consumer<?, ?> consumer) {
        handle(record, ack, consumer);
    }

    @KafkaListener(
            id = "job-transcoding-retry",
            topics = "${app.kafka.transcoding-retry-topic}",
            containerFactory = "transcodingContainerFactory")
    public void onTranscodingRetried(ConsumerRecord<String, String> record, Acknowledgment ack, Consumer<?, ?> consumer) {
        handle(record, ack, consumer);
    }

    private void handle(ConsumerRecord<String, String> record, Acknowledgment ack, Consumer<?, ?> consumer) {
        if (!JobRequest.targets(record, JobType.TRANSCODING)) {
            ack.acknowledge();
            return;
        }
        Long videoId = JobRequest.videoId(record);
        ProcessingJob job;
        try {
            job = jobService.getJob(videoId, JobType.TRANSCODING);
        } catch (JobNotFoundException e) {
            log.warn("존재하지 않는 job의 실행 요청을 버림: videoId={}, type={}", videoId, JobType.TRANSCODING);
            ack.acknowledge();
            return;
        }
        int attempt = (int) longHeader(record, ATTEMPT_HEADER, 1);
        long retryAt = longHeader(record, RETRY_AT_HEADER, 0);
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        ConsumerGroupMetadata group = consumer.groupMetadata();
        Long executionId = recorder.received(record, job.getId(), group.memberId(), group.generationId());

        MessageListenerContainer container = containerOf(partition);
        container.pausePartition(partition);
        transcodingExecutor.execute(() -> {
            try {
                waitUntil(retryAt);
                JobExecutionResult result = run(job);
                recorder.finished(executionId, result);
                if (result == JobExecutionResult.FAILED) {
                    handleFailure(videoId, attempt);
                }
                ack.acknowledge();
            } catch (InterruptedException e) {
                // 종료 중의 중단은 ack하지 않아 재전달 대상으로 남김
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                log.error("트랜스코딩 레코드 처리 중 예상 밖 예외: videoId={}", videoId, e);
                ack.acknowledge();
            } finally {
                container.resumePartition(partition);
            }
        });
    }

    private JobExecutionResult run(ProcessingJob job) {
        try {
            return jobWorker.execute(job);
        } catch (RuntimeException e) {
            log.error("job 실행 중 예상 밖 예외: jobId={}", job.getId(), e);
            return JobExecutionResult.FAILED;
        }
    }

    private void handleFailure(Long videoId, int attempt) throws InterruptedException {
        if (attempt < maxAttempts) {
            forwardToRetry(videoId, attempt + 1);
            return;
        }
        log.warn("재시도 소진: videoId={}, type={}", videoId, JobType.TRANSCODING);
        try {
            jobService.markExhausted(videoId, JobType.TRANSCODING);
        } catch (JobNotFoundException | InvalidJobTransitionException e) {
            log.warn("소진 처리 생략: videoId={}, 사유={}", videoId, e.getMessage());
        }
    }

    // 발행 성공 전의 ack는 위치 단위 커밋으로 실패 레코드를 건너뛰므로, 파티션을 멈춘 채 발행 성공까지 반복
    private void forwardToRetry(Long videoId, int nextAttempt) throws InterruptedException {
        long delay = retryDelayMs << (nextAttempt - 2);
        String value = String.valueOf(videoId);
        ProducerRecord<String, String> retry = new ProducerRecord<>(retryTopic, value, value);
        retry.headers().add(ATTEMPT_HEADER, longBytes(nextAttempt));
        retry.headers().add(RETRY_AT_HEADER, longBytes(System.currentTimeMillis() + delay));
        while (true) {
            try {
                kafkaTemplate.send(retry).get(10, TimeUnit.SECONDS);
                return;
            } catch (ExecutionException | TimeoutException e) {
                log.error("재시도 토픽 발행 실패, 다시 시도: videoId={}", videoId, e);
                Thread.sleep(1_000);
            }
        }
    }

    private void waitUntil(long epochMillis) throws InterruptedException {
        long wait = epochMillis - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
    }

    // 리스너마다 따로 있는 컨테이너 중 파티션 보유 컨테이너 탐색
    private MessageListenerContainer containerOf(TopicPartition partition) {
        return registry.getAllListenerContainers().stream()
                .filter(c -> c.getAssignedPartitions() != null && c.getAssignedPartitions().contains(partition))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("파티션을 가진 컨테이너 없음: " + partition));
    }

    private static long longHeader(ConsumerRecord<String, String> record, String name, long defaultValue) {
        Header header = record.headers().lastHeader(name);
        return header == null ? defaultValue : ByteBuffer.wrap(header.value()).getLong();
    }

    private static byte[] longBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }
}
