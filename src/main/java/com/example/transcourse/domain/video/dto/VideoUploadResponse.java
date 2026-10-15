package com.example.transcourse.domain.video.dto;

import com.example.transcourse.domain.video.entity.Video;

public record VideoUploadResponse(Long videoId, String originalName) {

    public static VideoUploadResponse from(Video video) {
        return new VideoUploadResponse(video.getId(), video.getOriginalName());
    }
}
