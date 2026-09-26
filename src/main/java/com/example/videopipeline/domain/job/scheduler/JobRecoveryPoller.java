package com.example.videopipeline.domain.job.scheduler;

import com.example.videopipeline.domain.job.entity.ProcessingJob;
import com.example.videopipeline.domain.job.messaging.JobEventPublisher;
import com.example.videopipeline.domain.job.service.JobService;
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
    private final Duration runningTimeout;
    private final Duration pendingStaleAfter;

    public JobRecoveryPoller(
            JobService jobService,
            JobEventPublisher publisher,
            @Value("${app.recovery.running-timeout}") Duration runningTimeout,
            @Value("${app.recovery.pending-stale-after}") Duration pendingStaleAfter) {
        this.jobService = jobService;
        this.publisher = publisher;
        this.runningTimeout = runningTimeout;
        this.pendingStaleAfter = pendingStaleAfter;
    }

    @Scheduled(fixedDelayString = "${app.recovery.poll-interval}")
    public void recover() {
        LocalDateTime now = LocalDateTime.now();

        List<ProcessingJob> zombies = jobService.failTimedOut(now.minus(runningTimeout));
        List<ProcessingJob> targets = jobService.findRecoverable(now.minus(pendingStaleAfter));
        if (zombies.isEmpty() && targets.isEmpty()) {
            return;
        }

        log.info("방치된 job 복구: RUNNING 좀비 {}건 실패 처리, {}건 재발행", zombies.size(), targets.size());
        targets.forEach(publisher::publish);
    }
}
