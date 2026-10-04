package com.example.transcourse.domain.job.messaging;

import com.example.transcourse.domain.job.entity.JobExecutionResult;
import com.example.transcourse.domain.job.exception.JobNotFoundException;
import com.example.transcourse.domain.job.experiment.WorkerExecutionRecorder;
import com.example.transcourse.domain.job.service.JobService;
import com.example.transcourse.domain.job.service.JobWorker;
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

// 인코딩은 max.poll.interval.ms를 넘기므로 워커 스레드에서 실행한다.
// pause/resume은 워커 스레드에서도 호출할 수 있는 컨테이너 API로 요청한다.
// 컨테이너가 리밸런스 후 재할당된 파티션을 다시 pause하므로, 재전달된 레코드는 진행 중 작업이 끝난 뒤 도착한다.
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
        } catch (RuntimeException e) {
            log.error("job 실행 중 예상 밖 예외: jobId={}", jobId, e);
            return JobExecutionResult.FAILED;
        }
    }

    private MessageListenerContainer container() {
        return registry.getListenerContainer(LISTENER_ID);
    }
}
