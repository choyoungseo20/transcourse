package com.example.transcourse.domain.job.experiment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.kafka.common.TopicPartition;

@Entity
@Table(name = "rebalance_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RebalanceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RebalanceEventType type;

    @Column(nullable = false)
    private String memberId;

    @Column(nullable = false)
    private int generationId;

    @Column(nullable = false)
    private String partitions;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    public static RebalanceEvent of(
            RebalanceEventType type, String memberId, int generationId, Collection<TopicPartition> partitions) {
        RebalanceEvent event = new RebalanceEvent();
        event.type = type;
        event.memberId = memberId;
        event.generationId = generationId;
        event.partitions = partitions.stream()
                .map(tp -> String.valueOf(tp.partition()))
                .sorted()
                .collect(Collectors.joining(","));
        event.occurredAt = LocalDateTime.now();
        return event;
    }
}
