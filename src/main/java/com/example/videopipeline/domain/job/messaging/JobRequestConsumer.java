package com.example.videopipeline.domain.job.messaging;

import com.example.videopipeline.domain.job.exception.JobNotFoundException;
import com.example.videopipeline.domain.job.service.JobExecutionResult;
import com.example.videopipeline.domain.job.service.JobService;
import com.example.videopipeline.domain.job.service.JobWorker;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
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
    private final KafkaListenerEndpointRegistry registry;
    private final Executor workerExecutor;

    public JobRequestConsumer(
            JobService jobService,
            JobWorker jobWorker,
            KafkaListenerEndpointRegistry registry,
            @Qualifier("jobWorkerExecutor") Executor workerExecutor) {
        this.jobService = jobService;
        this.jobWorker = jobWorker;
        this.registry = registry;
        this.workerExecutor = workerExecutor;
    }

    @KafkaListener(id = LISTENER_ID, idIsGroup = false, topics = "${app.kafka.jobs-topic}")
    public void onJobRequested(ConsumerRecord<String, String> record, Acknowledgment ack) {
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        Long jobId = Long.valueOf(record.value());

        container().pausePartition(partition);
        workerExecutor.execute(() -> {
            try {
                run(jobId);
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
