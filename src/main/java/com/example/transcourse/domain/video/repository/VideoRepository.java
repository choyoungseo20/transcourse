package com.example.transcourse.domain.video.repository;

import com.example.transcourse.domain.video.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<Video, Long> {

    @Modifying
    @Query("""
            update Video v
            set v.durationSec = :durationSec,
                v.width = :width,
                v.height = :height,
                v.videoCodec = :videoCodec,
                v.audioCodec = :audioCodec
            where v.id = :videoId
            """)
    void updateMetadata(
            @Param("videoId") Long videoId,
            @Param("durationSec") Double durationSec,
            @Param("width") Integer width,
            @Param("height") Integer height,
            @Param("videoCodec") String videoCodec,
            @Param("audioCodec") String audioCodec);

    @Modifying
    @Query("""
            update Video v
            set v.thumbnailPath = :thumbnailPath
            where v.id = :videoId
            """)
    void updateThumbnailPath(@Param("videoId") Long videoId, @Param("thumbnailPath") String thumbnailPath);

    @Modifying
    @Query("""
            update Video v
            set v.playlistPath = :playlistPath
            where v.id = :videoId
            """)
    void updatePlaylistPath(@Param("videoId") Long videoId, @Param("playlistPath") String playlistPath);
}
