package com.example.transcourse.domain.job.processor;

import com.example.transcourse.domain.job.entity.JobType;
import com.example.transcourse.domain.video.dto.VideoMetadata;
import com.example.transcourse.domain.video.service.VideoService;
import com.example.transcourse.global.storage.S3FileStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import net.bramp.ffmpeg.shared.CodecType;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataProcessor implements JobProcessor {

    private final S3FileStorage fileStorage;
    private final FFprobe ffprobe;
    private final VideoService videoService;

    @Override
    public JobType supportedType() {
        return JobType.METADATA;
    }

    @Override
    public void process(Long videoId, String filePath) {
        long startedAt = System.currentTimeMillis();
        Path original = fileStorage.downloadToTemp(filePath);
        try {
            VideoMetadata metadata = probe(original);
            videoService.applyMetadata(videoId, metadata);
            log.info("메타데이터 추출 완료: videoId={}, {}ms", videoId, System.currentTimeMillis() - startedAt);
        } finally {
            deleteQuietly(original);
        }
    }

    private VideoMetadata probe(Path file) {
        try {
            FFmpegProbeResult result = ffprobe.probe(file.toString());
            FFmpegStream videoStream = findStream(result, CodecType.VIDEO)
                    .orElseThrow(() -> new IllegalStateException("영상 스트림이 없는 파일"));
            String audioCodec = findStream(result, CodecType.AUDIO)
                    .map(stream -> stream.codec_name)
                    .orElse(null); // 무음 영상은 오디오 스트림이 없을 수 있다
            return new VideoMetadata(
                    result.getFormat().duration,
                    videoStream.width,
                    videoStream.height,
                    videoStream.codec_name,
                    audioCodec);
        } catch (IOException e) {
            throw new IllegalStateException("ffprobe 실패: " + e.getMessage(), e);
        }
    }

    private Optional<FFmpegStream> findStream(FFmpegProbeResult result, CodecType type) {
        return result.getStreams().stream()
                .filter(stream -> stream.codec_type == type)
                .findFirst();
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("임시 파일 삭제 실패: {}", file, e);
        }
    }
}
