package com.example.transcourse.domain.job.processor;

import com.example.transcourse.domain.job.entity.JobType;

public interface JobProcessor {

    JobType supportedType();

    void process(Long videoId, String filePath);
}
