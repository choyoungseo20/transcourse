package com.example.transcourse.global.config;

import com.example.transcourse.domain.job.experiment.RebalanceEventRepository;
import com.example.transcourse.domain.job.experiment.RebalanceRecorder;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
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
    public ThreadPoolTaskExecutor transcodingExecutor(@Value("${app.kafka.transcoding-threads}") int threads) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setThreadNamePrefix("transcoding-");
        return executor;
    }

    // 처리가 리스너 반환 뒤 워커 스레드에서 끝나므로 수동 ack를 쓴다
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> transcodingContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            RebalanceEventRepository rebalanceEventRepository) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setConsumerRebalanceListener(new RebalanceRecorder(rebalanceEventRepository));
        return factory;
    }
}
