package com.example.transcourse.global.config;

import com.example.transcourse.domain.job.experiment.RebalanceEventRepository;
import com.example.transcourse.domain.job.experiment.RebalanceRecorder;
import com.example.transcourse.domain.job.messaging.ShortJobConsumer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic jobsTopic(
            @Value("${app.kafka.jobs-topic}") String topic, @Value("${app.kafka.jobs-partitions}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    // 짧은 작업은 같은 자리에서 백오프 재시도
    // 트랜스코딩은 재시도 토픽 설정이 이 핸들러를 대체
    @Bean
    public DefaultErrorHandler jobErrorHandler(
            ShortJobConsumer shortJobConsumer,
            @Value("${app.kafka.job-attempts}") int attempts,
            @Value("${app.kafka.retry-delay-ms}") long retryDelayMs) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(attempts - 1);
        backOff.setInitialInterval(retryDelayMs);
        backOff.setMultiplier(2);
        return new DefaultErrorHandler(shortJobConsumer::onExhausted, backOff);
    }

    // 동시 실행 수의 상한은 이 풀이 아닌 할당 파티션 수
    // 파티션마다 진행 중인 레코드는 1건
    @Bean
    public ThreadPoolTaskExecutor transcodingExecutor(@Value("${app.kafka.transcoding-threads}") int threads) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setThreadNamePrefix("transcoding-");
        return executor;
    }

    // 리밸런싱 기록 대상을 트랜스코딩 그룹으로 한정하기 위한 전용 팩토리
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> transcodingContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            RebalanceEventRepository rebalanceEventRepository) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.getContainerProperties().setConsumerRebalanceListener(new RebalanceRecorder(rebalanceEventRepository));
        return factory;
    }
}
