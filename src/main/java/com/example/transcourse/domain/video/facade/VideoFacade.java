package com.example.transcourse.domain.video.facade;

import com.example.transcourse.domain.video.dto.ArtifactUrlResponse;
import com.example.transcourse.domain.video.dto.VideoUploadResponse;
import com.example.transcourse.domain.video.entity.Video;
import com.example.transcourse.domain.video.service.VideoService;
import com.example.transcourse.global.apipayload.ErrorStatus;
import com.example.transcourse.global.exception.GeneralException;
import com.example.transcourse.global.storage.S3FileStorage;
import com.example.transcourse.global.storage.StorageKeyFactory;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class VideoFacade {

    private final S3FileStorage s3FileStorage;
    private final StorageKeyFactory keyFactory;
    private final VideoService videoService;

    public VideoUploadResponse upload(MultipartFile file) {
        validate(file);

        String key = keyFactory.originalKey();
        s3FileStorage.save(file, key);

        try {
            return videoService.register(file.getOriginalFilename(), file.getSize(), key);
        } catch (RuntimeException e) {
            s3FileStorage.deleteQuietly(key);
            throw e;
        }
    }

    public ArtifactUrlResponse thumbnailUrl(Long videoId) {
        Video video = videoService.getVideo(videoId);
        ensureReady(video.getThumbnailPath());
        return ArtifactUrlResponse.of(s3FileStorage.presignGet(video.getThumbnailPath()));
    }

    public String hlsPlaylist(Long videoId) {
        Video video = videoService.getVideo(videoId);
        ensureReady(video.getPlaylistPath());
        String hlsPrefix = keyFactory.hlsPrefix(video.getFilePath());
        return s3FileStorage.readUtf8(video.getPlaylistPath()).lines()
                .map(line -> isSegmentLine(line) ? s3FileStorage.presignGet(hlsPrefix + line) : line)
                .collect(Collectors.joining("\n"));
    }

    private boolean isSegmentLine(String line) {
        return !line.isBlank() && !line.startsWith("#");
    }

    private void ensureReady(String artifactPath) {
        if (artifactPath == null) {
            throw new GeneralException(ErrorStatus.VIDEO_ARTIFACT_NOT_READY);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GeneralException(ErrorStatus.VIDEO_EMPTY_FILE);
        }
    }
}
