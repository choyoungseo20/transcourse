package com.example.transcourse.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic jobsTopic(
            @Value("${app.kafka.jobs-topic}") String topic, @Value("${app.kafka.jobs-partitions}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    // 동시 실행 수의 상한은 이 풀이 아니라 할당된 파티션 수다.
    // 파티션마다 진행 중인 레코드는 1건이다.
    @Bean
    public ThreadPoolTaskExecutor jobWorkerExecutor(@Value("${app.kafka.worker-threads}") int threads) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setThreadNamePrefix("job-worker-");
        return executor;
    }

    @Bean
    public ContainerCustomizer<Object, Object, ConcurrentMessageListenerContainer<Object, Object>> rebalanceListenerCustomizer(
            ConsumerAwareRebalanceListener rebalanceListener) {
        return container -> container.getContainerProperties().setConsumerRebalanceListener(rebalanceListener);
    }
}
