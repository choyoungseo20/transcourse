package com.example.transcourse.domain.video.dto;

public record ArtifactUrlResponse(String url) {

    public static ArtifactUrlResponse of(String url) {
        return new ArtifactUrlResponse(url);
    }
}
