package com.example.transcourse.domain.video.event;

import com.example.transcourse.domain.video.entity.Video;
import java.time.Instant;

public record VideoUploaded(Long videoId, Instant occurredAt) {

    public static VideoUploaded of(Video video) {
        return new VideoUploaded(video.getId(), Instant.now());
    }
}
