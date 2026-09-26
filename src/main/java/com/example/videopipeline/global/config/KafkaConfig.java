package com.example.videopipeline.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic jobsTopic(
            @Value("${app.kafka.jobs-topic}") String topic, @Value("${app.kafka.jobs-partitions}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    // 동시 실행 수는 이 풀이 아니라 할당된 파티션 수로 제한된다 (파티션당 진행 중 레코드 1건)
    @Bean
    public ThreadPoolTaskExecutor jobWorkerExecutor(@Value("${app.kafka.worker-threads}") int threads) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setThreadNamePrefix("job-worker-");
        return executor;
    }

}
