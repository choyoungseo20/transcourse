package com.example.transcourse.domain.video.entity;

import com.example.transcourse.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "video")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Video extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 원본 정보 — 업로드 시점에 채워진다
    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false)
    private Long fileSize;

    // 원본 메타데이터 — METADATA job이 채운다
    private Double durationSec;

    private Integer width;

    private Integer height;

    private String videoCodec;

    private String audioCodec;

    // 처리 결과 — THUMBNAIL / TRANSCODING job이 채운다
    private String thumbnailPath;

    private String playlistPath;

    @Builder
    private Video(String originalName, String filePath, Long fileSize) {
        this.originalName = originalName;
        this.filePath = filePath;
        this.fileSize = fileSize;
    }
}
