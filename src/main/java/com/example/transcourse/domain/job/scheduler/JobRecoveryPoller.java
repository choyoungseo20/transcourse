package com.example.transcourse.domain.job.scheduler;

import com.example.transcourse.domain.job.entity.ProcessingJob;
import com.example.transcourse.domain.job.messaging.JobEventPublisher;
import com.example.transcourse.domain.job.service.JobService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobRecoveryPoller {

    private final JobService jobService;
    private final JobEventPublisher publisher;
    private final Duration pendingStaleAfter;

    public JobRecoveryPoller(
            JobService jobService,
            JobEventPublisher publisher,
            @Value("${app.recovery.pending-stale-after}") Duration pendingStaleAfter) {
        this.jobService = jobService;
        this.publisher = publisher;
        this.pendingStaleAfter = pendingStaleAfter;
    }

    @Scheduled(fixedDelayString = "${app.recovery.poll-interval}")
    public void recover() {
        List<ProcessingJob> targets = jobService.findRecoverable(LocalDateTime.now().minus(pendingStaleAfter));
        if (targets.isEmpty()) {
            return;
        }

        log.info("방치된 job 재발행: {}건", targets.size());
        targets.forEach(publisher::publishRetry);
    }
}
