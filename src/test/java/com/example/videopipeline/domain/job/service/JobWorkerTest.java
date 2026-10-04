package com.example.videopipeline.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.videopipeline.domain.job.entity.JobExecutionResult;
import com.example.videopipeline.domain.job.entity.JobStatus;
import com.example.videopipeline.domain.job.entity.JobType;
import com.example.videopipeline.domain.job.entity.ProcessingJob;
import com.example.videopipeline.domain.job.exception.InvalidJobTransitionException;
import com.example.videopipeline.domain.job.exception.StaleJobAttemptException;
import com.example.videopipeline.domain.job.processor.JobProcessor;
import com.example.videopipeline.domain.video.service.VideoService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobWorkerTest {

    private static final String FILE_PATH = "videos/uuid/original";

    private final JobService jobService = mock(JobService.class);
    private final VideoService videoService = mock(VideoService.class);
    private final JobProcessor processor = mock(JobProcessor.class);

    private JobWorker worker;
    private ProcessingJob job;

    @BeforeEach
    void setUp() {
        given(processor.supportedType()).willReturn(JobType.METADATA);
        worker = new JobWorker(jobService, videoService, List.of(processor));
        job = ProcessingJob.create(1L, JobType.METADATA);
    }

    @Test
    void 실행_가능_상태가_아니면_처리_없이_건너뛴다() {
        given(jobService.markStarted(any()))
                .willThrow(new InvalidJobTransitionException(1L, JobStatus.RUNNING));

        JobExecutionResult result = worker.execute(job);

        assertThat(result).isEqualTo(JobExecutionResult.SKIPPED);
        verify(processor, never()).process(anyLong(), any());
        verify(jobService, never()).markFailed(any(), any(), anyInt());
    }

    @Test
    void 처리가_성공하면_같은_시도_번호로_성공을_기록한다() {
        given(jobService.markStarted(any())).willReturn(1);
        given(videoService.getFilePath(1L)).willReturn(FILE_PATH);

        JobExecutionResult result = worker.execute(job);

        assertThat(result).isEqualTo(JobExecutionResult.SUCCEEDED);
        verify(processor).process(1L, FILE_PATH);
        verify(jobService).markSucceeded(job.getId(), 1);
    }

    @Test
    void 처리가_실패하면_예외_메시지를_사유로_실패를_기록한다() {
        given(jobService.markStarted(any())).willReturn(1);
        given(videoService.getFilePath(1L)).willReturn(FILE_PATH);
        willThrow(new IllegalStateException("ffmpeg 실행 실패")).given(processor).process(1L, FILE_PATH);

        JobExecutionResult result = worker.execute(job);

        assertThat(result).isEqualTo(JobExecutionResult.FAILED);
        verify(jobService).markFailed(job.getId(), "ffmpeg 실행 실패", 1);
        verify(jobService, never()).markSucceeded(any(), anyInt());
    }

    @Test
    void 예외_메시지가_없으면_toString을_사유로_남긴다() {
        given(jobService.markStarted(any())).willReturn(1);
        given(videoService.getFilePath(1L)).willReturn(FILE_PATH);
        willThrow(new NullPointerException()).given(processor).process(1L, FILE_PATH);

        worker.execute(job);

        verify(jobService).markFailed(job.getId(), "java.lang.NullPointerException", 1);
    }

    @Test
    void 만료된_시도의_성공_기록_거부는_결과_거부로_반환하고_밖으로_전파되지_않는다() {
        given(jobService.markStarted(any())).willReturn(1);
        given(videoService.getFilePath(1L)).willReturn(FILE_PATH);
        willThrow(new StaleJobAttemptException(1L, 1, 2))
                .given(jobService).markSucceeded(any(), anyInt());

        assertThat(worker.execute(job)).isEqualTo(JobExecutionResult.RESULT_REJECTED);
        verify(jobService).markSucceeded(job.getId(), 1);
    }

    @Test
    void 상태_전이_거부로_실패_기록이_막혀도_밖으로_전파되지_않는다() {
        given(jobService.markStarted(any())).willReturn(1);
        given(videoService.getFilePath(1L)).willReturn(FILE_PATH);
        willThrow(new IllegalStateException("처리 실패")).given(processor).process(1L, FILE_PATH);
        willThrow(new InvalidJobTransitionException(1L, JobStatus.FAILED))
                .given(jobService).markFailed(any(), any(), anyInt());

        assertThatCode(() -> worker.execute(job)).doesNotThrowAnyException();
        verify(jobService).markFailed(job.getId(), "처리 실패", 1);
    }
}
