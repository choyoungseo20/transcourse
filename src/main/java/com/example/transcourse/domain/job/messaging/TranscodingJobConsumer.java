package com.example.transcourse.domain.job.messaging;

import com.example.transcourse.domain.job.entity.JobExecutionResult;
import com.example.transcourse.domain.job.entity.JobType;
import com.example.transcourse.domain.job.entity.ProcessingJob;
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

// max.poll.interval.ms를 넘기는 인코딩의 워커 스레드 실행
// 워커 스레드에서도 호출 가능한 컨테이너 API로의 pause·resume 요청
// 리밸런스 후 재할당된 파티션에 대한 컨테이너의 pause 재적용
// 재할당 파티션의 재전달 레코드는 진행 중 작업 종료 뒤 도착
@Slf4j
@Component
public class TranscodingJobConsumer {

    static final String LISTENER_ID = "job-transcoding";

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

    @KafkaListener(
            id = LISTENER_ID,
            topics = "${app.kafka.jobs-topic}",
            containerFactory = "transcodingContainerFactory")
    public void onTranscodingRequested(ConsumerRecord<String, String> record, Acknowledgment ack, Consumer<?, ?> consumer) {
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
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        ConsumerGroupMetadata group = consumer.groupMetadata();
        Long executionId = recorder.received(record, job.getId(), group.memberId(), group.generationId());

        container().pausePartition(partition);
        transcodingExecutor.execute(() -> {
            try {
                recorder.finished(executionId, run(job));
            } finally {
                ack.acknowledge();
                container().resumePartition(partition);
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

    private MessageListenerContainer container() {
        return registry.getListenerContainer(LISTENER_ID);
    }
}
