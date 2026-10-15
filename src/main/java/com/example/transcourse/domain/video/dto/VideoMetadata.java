package com.example.transcourse.domain.video.dto;

public record VideoMetadata(
        Double durationSec,
        Integer width,
        Integer height,
        String videoCodec,
        String audioCodec) {
}
