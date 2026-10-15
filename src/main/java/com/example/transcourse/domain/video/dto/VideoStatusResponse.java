package com.example.transcourse.domain.video.dto;

import com.example.transcourse.domain.job.entity.JobStatus;
import com.example.transcourse.domain.job.entity.JobType;
import com.example.transcourse.domain.job.entity.OverallStatus;
import com.example.transcourse.domain.job.entity.ProcessingJob;
import com.example.transcourse.domain.video.entity.Video;
import java.time.LocalDateTime;
import java.util.List;

public record VideoStatusResponse(
        Long videoId,
        String originalName,
        Long fileSize,
        OverallStatus overallStatus,
        Double durationSec,
        Integer width,
        Integer height,
        String videoCodec,
        String audioCodec,
        String thumbnailPath,
        String playlistPath,
        List<JobResponse> jobs) {

    public record JobResponse(
            JobType type,
            JobStatus status,
            int attemptCount,
            String lastFailureReason,
            LocalDateTime startedAt,
            LocalDateTime finishedAt) {

        private static JobResponse from(ProcessingJob job) {
            return new JobResponse(
                    job.getType(),
                    job.getStatus(),
                    job.getAttemptCount(),
                    job.getLastFailureReason(),
                    job.getStartedAt(),
                    job.getFinishedAt());
        }
    }

    public static VideoStatusResponse of(Video video, List<ProcessingJob> jobs, OverallStatus overallStatus) {
        return new VideoStatusResponse(
                video.getId(),
                video.getOriginalName(),
                video.getFileSize(),
                overallStatus,
                video.getDurationSec(),
                video.getWidth(),
                video.getHeight(),
                video.getVideoCodec(),
                video.getAudioCodec(),
                video.getThumbnailPath(),
                video.getPlaylistPath(),
                jobs.stream().map(JobResponse::from).toList());
    }
}
