package com.example.transcourse.domain.video.service;

import com.example.transcourse.domain.job.entity.OverallStatus;
import com.example.transcourse.domain.job.entity.ProcessingJob;
import com.example.transcourse.domain.job.service.JobService;
import com.example.transcourse.domain.video.dto.VideoMetadata;
import com.example.transcourse.domain.video.dto.VideoStatusResponse;
import com.example.transcourse.domain.video.dto.VideoUploadResponse;
import com.example.transcourse.domain.video.entity.Video;
import com.example.transcourse.domain.video.event.VideoUploaded;
import com.example.transcourse.domain.video.exception.VideoNotFoundException;
import com.example.transcourse.domain.video.repository.VideoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final JobService jobService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public VideoUploadResponse register(String originalName, long fileSize, String key) {
        Video video = Video.builder()
                .originalName(originalName)
                .filePath(key)
                .fileSize(fileSize)
                .build();
        videoRepository.save(video);

        jobService.createAllFor(video.getId());

        // AFTER_COMMIT 리스너 수신을 위한 트랜잭션 안에서의 발행
        eventPublisher.publishEvent(VideoUploaded.of(video));

        return VideoUploadResponse.from(video);
    }

    @Transactional(readOnly = true)
    public String getFilePath(Long videoId) {
        return getVideo(videoId).getFilePath();
    }

    @Transactional(readOnly = true)
    public Video getVideo(Long videoId) {
        return videoRepository.findById(videoId)
                .orElseThrow(() -> new VideoNotFoundException(videoId));
    }

    @Transactional
    public void applyMetadata(Long videoId, VideoMetadata metadata) {
        videoRepository.updateMetadata(
                videoId,
                metadata.durationSec(),
                metadata.width(),
                metadata.height(),
                metadata.videoCodec(),
                metadata.audioCodec());
    }

    @Transactional
    public void applyThumbnail(Long videoId, String thumbnailPath) {
        videoRepository.updateThumbnailPath(videoId, thumbnailPath);
    }

    @Transactional
    public void applyPlaylist(Long videoId, String playlistPath) {
        videoRepository.updatePlaylistPath(videoId, playlistPath);
    }

    @Transactional(readOnly = true)
    public VideoStatusResponse getStatus(Long videoId) {
        Video video = getVideo(videoId);
        List<ProcessingJob> jobs = jobService.findAllFor(videoId);
        return VideoStatusResponse.of(video, jobs, OverallStatus.from(jobs));
    }
}
