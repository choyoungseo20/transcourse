package com.example.transcourse.domain.job.listener;

import com.example.transcourse.domain.job.messaging.JobEventPublisher;
import com.example.transcourse.domain.job.repository.ProcessingJobRepository;
import com.example.transcourse.domain.video.event.VideoUploaded;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class VideoUploadedEventListener {

    private final ProcessingJobRepository jobRepository;
    private final JobEventPublisher publisher;

    @TransactionalEventListener
    public void handle(VideoUploaded event) {
        jobRepository.findByVideoId(event.videoId())
                .forEach(publisher::publish);
    }
}
