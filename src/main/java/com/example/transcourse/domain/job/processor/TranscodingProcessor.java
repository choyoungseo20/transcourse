package com.example.transcourse.domain.job.processor;

import com.example.transcourse.domain.job.entity.JobType;
import com.example.transcourse.domain.video.service.VideoService;
import com.example.transcourse.global.storage.S3FileStorage;
import com.example.transcourse.global.storage.StorageKeyFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranscodingProcessor implements JobProcessor {

    // 단일 렌디션 720p
    // 더 작은 원본의 업스케일 생략
    private static final String SCALE_FILTER = "scale=-2:'min(720,ih)'";
    private static final long SEGMENT_SECONDS = 4;
    private static final String SEGMENT_FILE_PATTERN = "segment-%03d.ts";

    private final S3FileStorage fileStorage;
    private final FFmpeg ffmpeg;
    private final StorageKeyFactory keyFactory;
    private final VideoService videoService;

    @Override
    public JobType supportedType() {
        return JobType.TRANSCODING;
    }

    @Override
    public void process(Long videoId, String filePath) {
        long startedAt = System.currentTimeMillis();
        Path original = fileStorage.downloadToTemp(filePath);
        Path outputDir = null;
        try {
            outputDir = createOutputDir();
            transcode(original, outputDir);
            fileStorage.saveDirectory(outputDir, keyFactory.hlsPrefix(filePath));
            videoService.applyPlaylist(videoId, keyFactory.hlsPlaylistKey(filePath));
            log.info("트랜스코딩 완료: videoId={}, {}ms", videoId, System.currentTimeMillis() - startedAt);
        } finally {
            deleteQuietly(original);
            deleteRecursively(outputDir);
        }
    }

    private void transcode(Path input, Path outputDir) {
        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(input.toString())
                .done()
                .overrideOutputFiles(true)
                .addHlsOutput(outputDir.resolve(StorageKeyFactory.HLS_PLAYLIST_NAME).toString())
                .setVideoCodec("libx264")
                .setAudioCodec("aac")
                .setVideoFilter(SCALE_FILTER)
                .addExtraArgs("-force_key_frames", "expr:gte(t,n_forced*" + SEGMENT_SECONDS + ")")
                .addExtraArgs("-sc_threshold", "0")
                .setHlsTime(SEGMENT_SECONDS, TimeUnit.SECONDS)
                .setHlsListSize(0) // VOD라 전체 세그먼트를 플레이리스트에 유지
                .setHlsSegmentFileName(outputDir.resolve(SEGMENT_FILE_PATTERN).toString())
                .done();
        try {
            new FFmpegExecutor(ffmpeg).createJob(builder).run();
        } catch (IOException e) {
            throw new IllegalStateException("ffmpeg 실행 실패: " + e.getMessage(), e);
        }
        ensurePlaylistExists(outputDir);
    }

    private Path createOutputDir() {
        try {
            return Files.createTempDirectory("hls-");
        } catch (IOException e) {
            throw new IllegalStateException("HLS 임시 디렉터리 생성 실패", e);
        }
    }

    private void ensurePlaylistExists(Path outputDir) {
        Path playlist = outputDir.resolve(StorageKeyFactory.HLS_PLAYLIST_NAME);
        try {
            if (!Files.exists(playlist) || Files.size(playlist) == 0) {
                throw new IllegalStateException("트랜스코딩 결과 플레이리스트가 비어 있음");
            }
        } catch (IOException e) {
            throw new IllegalStateException("트랜스코딩 결과 확인 실패", e);
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("임시 파일 삭제 실패: {}", file, e);
        }
    }

    private void deleteRecursively(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (IOException e) {
            log.warn("임시 디렉터리 삭제 실패: {}", dir, e);
        }
    }
}
