package com.example.transcourse.domain.job.controller;

import com.example.transcourse.domain.job.entity.JobType;
import com.example.transcourse.domain.job.entity.ProcessingJob;
import com.example.transcourse.domain.job.messaging.JobEventPublisher;
import com.example.transcourse.domain.job.service.JobService;
import com.example.transcourse.global.apipayload.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/videos/{videoId}/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final JobEventPublisher publisher;

    @PostMapping("/{type}/retry")
    public CommonResponse<Void> retry(@PathVariable Long videoId, @PathVariable JobType type) {
        ProcessingJob job = jobService.resetForRetry(videoId, type);
        publisher.publish(job);
        return CommonResponse.onSuccess(null);
    }
}
