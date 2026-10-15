package com.example.transcourse.domain.job.messaging;

import com.example.transcourse.domain.job.entity.JobType;
import com.example.transcourse.domain.job.entity.ProcessingJob;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// 발행 실패로 PENDING에 남은 job의 복구 폴러 재발행
@Slf4j
@Component
public class JobEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public JobEventPublisher(KafkaTemplate<String, String> kafkaTemplate, @Value("${app.kafka.jobs-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publishUploaded(Long videoId) {
        send(videoId, null);
    }

    // 재전달 선점에 의한 다른 유형의 진행 중 시도 무효화를 막기 위한 유형 지정
    public void publishRetry(ProcessingJob job) {
        send(job.getVideoId(), job.getType());
    }

    private void send(Long videoId, JobType targetType) {
        String value = String.valueOf(videoId);
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, value, value);
        if (targetType != null) {
            record.headers().add(JobRequest.TARGET_TYPE_HEADER, targetType.name().getBytes(StandardCharsets.UTF_8));
        }
        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("job 실행 요청 발행 실패: videoId={}, targetType={}", videoId, targetType, ex);
            }
        });
    }
}
