package com.example.videopipeline.domain.job.messaging;

import com.example.videopipeline.domain.job.entity.RebalanceEvent;
import com.example.videopipeline.domain.job.entity.RebalanceEventType;
import com.example.videopipeline.domain.job.repository.RebalanceEventRepository;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RebalanceRecorder implements ConsumerAwareRebalanceListener {

    private final RebalanceEventRepository repository;

    @Override
    public void onPartitionsRevokedBeforeCommit(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        record(consumer, RebalanceEventType.REVOKED, partitions);
    }

    @Override
    public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        record(consumer, RebalanceEventType.ASSIGNED, partitions);
    }

    @Override
    public void onPartitionsLost(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        record(consumer, RebalanceEventType.LOST, partitions);
    }

    private void record(Consumer<?, ?> consumer, RebalanceEventType type, Collection<TopicPartition> partitions) {
        ConsumerGroupMetadata group = consumer.groupMetadata();
        log.info("[rebalance] {} member={} generation={} partitions={}",
                type, group.memberId(), group.generationId(), partitions);
        repository.save(RebalanceEvent.of(type, group.memberId(), group.generationId(), partitions));
    }
}
