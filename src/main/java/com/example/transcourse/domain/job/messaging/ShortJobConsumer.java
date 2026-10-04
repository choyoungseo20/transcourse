package com.example.transcourse.domain.job.messaging;

import com.example.transcourse.domain.job.entity.JobExecutionResult;
import com.example.transcourse.domain.job.entity.JobType;
import com.example.transcourse.domain.job.exception.InvalidJobTransitionException;
import com.example.transcourse.domain.job.exception.JobNotFoundException;
import com.example.transcourse.domain.job.service.JobService;
import com.example.transcourse.domain.job.service.JobWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShortJobConsumer {

    private final JobService jobService;
    private final JobWorker jobWorker;

    @KafkaListener(id = "job-metadata", topics = "${app.kafka.jobs-topic}")
    public void onMetadataRequested(ConsumerRecord<String, String> record) {
        handle(record, JobType.METADATA);
    }

    @KafkaListener(id = "job-thumbnail", topics = "${app.kafka.jobs-topic}")
    public void onThumbnailRequested(ConsumerRecord<String, String> record) {
        handle(record, JobType.THUMBNAIL);
    }

    // 에러 핸들러의 재시도 소진 시 호출
    public void onExhausted(ConsumerRecord<?, ?> record, Exception exception) {
        if (!(exception.getCause() instanceof ShortJobFailedException failed)) {
            log.error("재시도 소진된 레코드를 버림: {}", record, exception);
            return;
        }
        log.warn("재시도 소진: videoId={}, type={}", failed.videoId, failed.type);
        try {
            jobService.markExhausted(failed.videoId, failed.type);
        } catch (JobNotFoundException | InvalidJobTransitionException e) {
            log.warn("소진 처리 생략: videoId={}, 사유={}", failed.videoId, e.getMessage());
        }
    }

    private void handle(ConsumerRecord<String, String> record, JobType type) {
        if (!JobRequest.targets(record, type)) {
            return;
        }
        Long videoId = JobRequest.videoId(record);
        JobExecutionResult result;
        try {
            result = jobWorker.execute(jobService.getJob(videoId, type));
        } catch (JobNotFoundException e) {
            log.warn("존재하지 않는 job의 실행 요청을 버림: videoId={}, type={}", videoId, type);
            return;
        }
        if (result == JobExecutionResult.FAILED) {
            throw new ShortJobFailedException(videoId, type);
        }
    }

    static class ShortJobFailedException extends RuntimeException {

        private final Long videoId;
        private final JobType type;

        ShortJobFailedException(Long videoId, JobType type) {
            super("job 실패: videoId=%d, type=%s".formatted(videoId, type));
            this.videoId = videoId;
            this.type = type;
        }
    }
}
