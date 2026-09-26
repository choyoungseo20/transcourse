package com.example.videopipeline.domain.job.messaging;

import com.example.videopipeline.domain.job.exception.JobNotFoundException;
import com.example.videopipeline.domain.job.service.JobExecutionResult;
import com.example.videopipeline.domain.job.service.JobService;
import com.example.videopipeline.domain.job.service.JobWorker;
import com.example.videopipeline.domain.job.service.WorkerExecutionRecorder;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

// 컨슈머 스레드는 poll()만 유지하고 인코딩은 워커 스레드에서 한다.
// pause/resume은 Consumer 객체가 아니라 컨테이너 API로 요청한다 — 워커 스레드에서 resume하려면 이 방법뿐이고,
// 컨테이너가 리밸런스 후 재할당된 파티션을 다시 pause해 준다는 차이가 있다.
@Slf4j
@Component
public class JobRequestConsumer {

    static final String LISTENER_ID = "video-jobs";

    private final JobService jobService;
    private final JobWorker jobWorker;
    private final WorkerExecutionRecorder recorder;
    private final KafkaListenerEndpointRegistry registry;
    private final Executor workerExecutor;

    public JobRequestConsumer(
            JobService jobService,
            JobWorker jobWorker,
            WorkerExecutionRecorder recorder,
            KafkaListenerEndpointRegistry registry,
            @Qualifier("jobWorkerExecutor") Executor workerExecutor) {
        this.jobService = jobService;
        this.jobWorker = jobWorker;
        this.recorder = recorder;
        this.registry = registry;
        this.workerExecutor = workerExecutor;
    }

    @KafkaListener(id = LISTENER_ID, idIsGroup = false, topics = "${app.kafka.jobs-topic}")
    public void onJobRequested(ConsumerRecord<String, String> record, Acknowledgment ack, Consumer<?, ?> consumer) {
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        ConsumerGroupMetadata group = consumer.groupMetadata();
        Long jobId = Long.valueOf(record.value());
        Long executionId = recorder.received(record, jobId, group.memberId(), group.generationId());

        container().pausePartition(partition);
        workerExecutor.execute(() -> {
            try {
                recorder.finished(executionId, run(jobId));
            } catch (RuntimeException e) {
                log.error("job 실행 중 예상 밖 예외: jobId={}", jobId, e);
                recorder.finished(executionId, JobExecutionResult.FAILED);
            } finally {
                ack.acknowledge();
                container().resumePartition(partition);
            }
        });
    }

    private JobExecutionResult run(Long jobId) {
        try {
            return jobWorker.execute(jobService.getJob(jobId));
        } catch (JobNotFoundException e) {
            log.warn("존재하지 않는 job의 실행 요청을 버림: jobId={}", jobId);
            return JobExecutionResult.SKIPPED;
        }
    }

    private MessageListenerContainer container() {
        return registry.getListenerContainer(LISTENER_ID);
    }
}
