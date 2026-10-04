package com.example.transcourse.domain.job.messaging;

import com.example.transcourse.domain.job.entity.JobType;
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

    private void handle(ConsumerRecord<String, String> record, JobType type) {
        if (!JobRequest.targets(record, type)) {
            return;
        }
        Long videoId = JobRequest.videoId(record);
        try {
            jobWorker.execute(jobService.getJob(videoId, type));
        } catch (JobNotFoundException e) {
            log.warn("존재하지 않는 job의 실행 요청을 버림: videoId={}, type={}", videoId, type);
        }
    }
}
