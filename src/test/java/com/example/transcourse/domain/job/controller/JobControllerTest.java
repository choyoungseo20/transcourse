package com.example.transcourse.domain.job.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.transcourse.domain.job.entity.JobStatus;
import com.example.transcourse.domain.job.entity.JobType;
import com.example.transcourse.domain.job.entity.ProcessingJob;
import com.example.transcourse.domain.job.exception.JobNotFoundException;
import com.example.transcourse.domain.job.exception.JobNotRetryableException;
import com.example.transcourse.domain.job.messaging.JobEventPublisher;
import com.example.transcourse.domain.job.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private JobEventPublisher publisher;

    @Test
    void 재시도에_성공하면_200과_공통_성공_코드를_반환한다() throws Exception {
        ProcessingJob job = ProcessingJob.create(1L, JobType.METADATA);
        given(jobService.resetForRetry(1L, JobType.METADATA)).willReturn(job);

        mockMvc.perform(post("/videos/1/jobs/METADATA/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON200"));

        verify(publisher).publishRetry(any(ProcessingJob.class));
    }

    @Test
    void 존재하지_않는_job의_재시도는_404와_JOB4001을_반환한다() throws Exception {
        given(jobService.resetForRetry(1L, JobType.METADATA))
                .willThrow(new JobNotFoundException(1L, JobType.METADATA));

        mockMvc.perform(post("/videos/1/jobs/METADATA/retry"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB4001"));
    }

    @Test
    void 재시도_불가_상태면_409와_JOB4002를_반환한다() throws Exception {
        given(jobService.resetForRetry(1L, JobType.METADATA))
                .willThrow(new JobNotRetryableException(1L, JobStatus.SUCCEEDED));

        mockMvc.perform(post("/videos/1/jobs/METADATA/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB4002"));
    }
}
