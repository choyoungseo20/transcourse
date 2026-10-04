package com.example.videopipeline.domain.job.messaging;

import com.example.videopipeline.domain.job.entity.ProcessingJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// 발행에 실패해 PENDING으로 남은 job은 복구 폴러가 다시 발행한다
@Slf4j
@Component
public class JobEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public JobEventPublisher(KafkaTemplate<String, String> kafkaTemplate, @Value("${app.kafka.jobs-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(ProcessingJob job) {
        String jobId = String.valueOf(job.getId());
        kafkaTemplate.send(topic, jobId, jobId).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("job 실행 요청 발행 실패: jobId={}, type={}", job.getId(), job.getType(), ex);
            }
        });
    }
}
