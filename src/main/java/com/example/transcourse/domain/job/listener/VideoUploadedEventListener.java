package com.example.transcourse.domain.job.listener;

import com.example.transcourse.domain.job.messaging.JobEventPublisher;
import com.example.transcourse.domain.video.event.VideoUploaded;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class VideoUploadedEventListener {

    private final JobEventPublisher publisher;

    @TransactionalEventListener
    public void handle(VideoUploaded event) {
        publisher.publishUploaded(event.videoId());
    }
}
