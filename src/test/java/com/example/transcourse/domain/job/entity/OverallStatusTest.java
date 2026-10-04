package com.example.transcourse.domain.job.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OverallStatusTest {

    @Test
    void 전부_SUCCEEDED면_COMPLETED다() {
        List<ProcessingJob> jobs = List.of(succeeded(), succeeded(), succeeded());

        assertThat(OverallStatus.from(jobs)).isEqualTo(OverallStatus.COMPLETED);
    }

    @Test
    void 하나라도_EXHAUSTED면_FAILED다() {
        List<ProcessingJob> jobs = List.of(succeeded(), succeeded(), exhausted());

        assertThat(OverallStatus.from(jobs)).isEqualTo(OverallStatus.FAILED);
    }

    @Test
    void FAILED는_재시도_여지가_있으므로_PROCESSING이다() {
        List<ProcessingJob> jobs = List.of(succeeded(), failed(), pending());

        assertThat(OverallStatus.from(jobs)).isEqualTo(OverallStatus.PROCESSING);
    }

    @Test
    void 진행_중이_섞여_있으면_PROCESSING이다() {
        List<ProcessingJob> jobs = List.of(succeeded(), running(), pending());

        assertThat(OverallStatus.from(jobs)).isEqualTo(OverallStatus.PROCESSING);
    }

    private ProcessingJob pending() {
        return ProcessingJob.create(1L, JobType.METADATA);
    }

    private ProcessingJob running() {
        ProcessingJob job = pending();
        job.start();
        return job;
    }

    private ProcessingJob succeeded() {
        ProcessingJob job = running();
        job.succeed(job.getAttemptCount());
        return job;
    }

    private ProcessingJob failed() {
        ProcessingJob job = running();
        job.fail("실패", job.getAttemptCount());
        return job;
    }

    private ProcessingJob exhausted() {
        ProcessingJob job = pending();
        for (int i = 0; i < 3; i++) {
            job.start();
            job.fail("실패", job.getAttemptCount());
        }
        return job;
    }
}
