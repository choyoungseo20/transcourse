package com.example.transcourse.domain.job.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.transcourse.domain.job.exception.InvalidJobTransitionException;
import com.example.transcourse.domain.job.exception.StaleJobAttemptException;
import org.junit.jupiter.api.Test;

class ProcessingJobTest {

    @Test
    void 생성_직후는_PENDING이고_시도_횟수는_0이다() {
        ProcessingJob job = newJob();

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getAttemptCount()).isZero();
    }

    @Test
    void 시작하면_RUNNING으로_전이하고_시도_횟수가_오른다() {
        ProcessingJob job = newJob();

        job.start();

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getStartedAt()).isNotNull();
        assertThat(job.getFinishedAt()).isNull();
    }

    @Test
    void FAILED에서_다시_시작할_수_있다() {
        ProcessingJob job = failedJob();

        job.start();

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void RUNNING에서_다시_시작하면_새_시도로_선점하고_이전_시도의_결과는_거부된다() {
        ProcessingJob job = newJob();
        job.start();                       // 워커 A의 시도 1
        job.start();                       // 재전달된 레코드를 받은 워커 B의 시도 2

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThatThrownBy(() -> job.succeed(1)) // 워커 A의 뒤늦은 성공 보고
                .isInstanceOf(StaleJobAttemptException.class);

        job.succeed(2);
        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void SUCCEEDED에서는_시작할_수_없다() {
        assertThatThrownBy(succeededJob()::start)
                .isInstanceOf(InvalidJobTransitionException.class);
    }

    @Test
    void EXHAUSTED에서는_시작할_수_없다() {
        assertThatThrownBy(exhaustedJob()::start)
                .isInstanceOf(InvalidJobTransitionException.class);
    }

    @Test
    void 현재_시도의_성공_기록은_SUCCEEDED로_전이한다() {
        ProcessingJob job = newJob();
        job.start();

        job.succeed(1);

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void RUNNING이_아니면_성공을_기록할_수_없다() {
        assertThatThrownBy(() -> newJob().succeed(0))
                .isInstanceOf(InvalidJobTransitionException.class);
    }

    @Test
    void RUNNING이_아니면_실패를_기록할_수_없다() {
        assertThatThrownBy(() -> newJob().fail("실패", 0))
                .isInstanceOf(InvalidJobTransitionException.class);
    }

    @Test
    void 만료된_시도의_성공_기록은_거부된다() {
        ProcessingJob job = failedJob();
        job.start(); // attempt 2

        assertThatThrownBy(() -> job.succeed(1))
                .isInstanceOf(StaleJobAttemptException.class);
    }

    @Test
    void 만료된_시도의_실패_기록은_거부된다() {
        ProcessingJob job = failedJob();
        job.start(); // attempt 2

        assertThatThrownBy(() -> job.fail("늦은 실패", 1))
                .isInstanceOf(StaleJobAttemptException.class);
    }

    @Test
    void 실패하면_FAILED가_되고_사유를_남긴다() {
        ProcessingJob job = newJob();
        job.start();

        job.fail("ffprobe 실패", 1);

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getLastFailureReason()).isEqualTo("ffprobe 실패");
    }

    @Test
    void 실패가_3회_누적되면_EXHAUSTED로_종착한다() {
        ProcessingJob job = exhaustedJob();

        assertThat(job.getStatus()).isEqualTo(JobStatus.EXHAUSTED);
        assertThat(job.getAttemptCount()).isEqualTo(3);
    }

    @Test
    void 수동_재시도는_FAILED를_PENDING으로_되돌리고_시각과_사유를_비운다() {
        ProcessingJob job = failedJob();

        job.resetForManualRetry();

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getStartedAt()).isNull();
        assertThat(job.getFinishedAt()).isNull();
        assertThat(job.getLastFailureReason()).isNull();
    }

    @Test
    void 수동_재시도는_EXHAUSTED를_PENDING으로_되돌린다() {
        ProcessingJob job = exhaustedJob();

        job.resetForManualRetry();

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    void RUNNING에서는_수동_재시도할_수_없다() {
        ProcessingJob job = newJob();
        job.start();

        assertThatThrownBy(job::resetForManualRetry)
                .isInstanceOf(InvalidJobTransitionException.class);
    }

    @Test
    void SUCCEEDED에서는_수동_재시도할_수_없다() {
        assertThatThrownBy(succeededJob()::resetForManualRetry)
                .isInstanceOf(InvalidJobTransitionException.class);
    }

    @Test
    void 수동_재시도는_시도_횟수를_되돌리지_않는다() { // ABA 회귀 — 번호 재사용은 fence를 무력화한다
        ProcessingJob job = exhaustedJob();

        job.resetForManualRetry();
        assertThat(job.getAttemptCount()).isEqualTo(3);

        job.start();
        assertThat(job.getAttemptCount()).isEqualTo(4);
    }

    @Test
    void 수동_재시도_후_다시_실패하면_즉시_EXHAUSTED다() { // 수동 재시도 = 1회의 추가 기회
        ProcessingJob job = exhaustedJob();
        job.resetForManualRetry();
        job.start();

        job.fail("재실패", 4);

        assertThat(job.getStatus()).isEqualTo(JobStatus.EXHAUSTED);
    }

    @Test
    void isRetryable은_FAILED와_EXHAUSTED에서만_참이다() {
        assertThat(failedJob().isRetryable()).isTrue();
        assertThat(exhaustedJob().isRetryable()).isTrue();
        assertThat(newJob().isRetryable()).isFalse();
        assertThat(succeededJob().isRetryable()).isFalse();
    }

    @Test
    void 좀비_판정_후_재실행되면_만료된_시도의_기록은_거부되고_현재_시도만_통과한다() {
        ProcessingJob job = newJob();
        job.start();                       // attempt 1 — 워커 A가 처리 중
        job.fail("실행 타임아웃 초과", 1);      // 폴러가 좀비 판정
        job.start();                       // 재실행 → attempt 2

        assertThatThrownBy(() -> job.succeed(1)) // 워커 A의 뒤늦은 성공 보고
                .isInstanceOf(StaleJobAttemptException.class);
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);

        job.succeed(2);
        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }

    private ProcessingJob newJob() {
        return ProcessingJob.create(1L, JobType.METADATA);
    }

    private ProcessingJob succeededJob() {
        ProcessingJob job = newJob();
        job.start();
        job.succeed(job.getAttemptCount());
        return job;
    }

    private ProcessingJob failedJob() {
        ProcessingJob job = newJob();
        job.start();
        job.fail("실패", job.getAttemptCount());
        return job;
    }

    private ProcessingJob exhaustedJob() {
        ProcessingJob job = newJob();
        for (int i = 0; i < 3; i++) {
            job.start();
            job.fail("실패", job.getAttemptCount());
        }
        return job;
    }
}
