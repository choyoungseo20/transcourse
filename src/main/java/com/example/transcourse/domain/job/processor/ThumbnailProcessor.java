package com.example.transcourse.domain.job.processor;

import com.example.transcourse.domain.job.entity.JobType;
import com.example.transcourse.domain.video.service.VideoService;
import com.example.transcourse.global.storage.S3FileStorage;
import com.example.transcourse.global.storage.StorageKeyFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThumbnailProcessor implements JobProcessor {

    private static final long CAPTURE_OFFSET_SECONDS = 1;

    private final S3FileStorage fileStorage;
    private final FFmpeg ffmpeg;
    private final StorageKeyFactory keyFactory;
    private final VideoService videoService;

    @Override
    public JobType supportedType() {
        return JobType.THUMBNAIL;
    }

    @Override
    public void process(Long videoId, String filePath) {
        long startedAt = System.currentTimeMillis();
        Path original = fileStorage.downloadToTemp(filePath);
        Path thumbnail = null;
        try {
            thumbnail = createOutputFile();
            extractFrame(original, thumbnail);
            String key = keyFactory.thumbnailKey(filePath);
            fileStorage.saveFile(thumbnail, key);
            videoService.applyThumbnail(videoId, key);
            log.info("썸네일 생성 완료: videoId={}, {}ms", videoId, System.currentTimeMillis() - startedAt);
        } finally {
            deleteQuietly(original);
            deleteQuietly(thumbnail);
        }
    }

    private void extractFrame(Path input, Path output) {
        try {
            runFfmpeg(input, output, CAPTURE_OFFSET_SECONDS);
            ensureNotEmpty(output);
        } catch (RuntimeException e) {
            log.info("오프셋 {}초 프레임 추출 실패, 첫 프레임으로 재시도: {}", CAPTURE_OFFSET_SECONDS, input);
            runFfmpeg(input, output, 0);
            ensureNotEmpty(output);
        }
    }

    private void runFfmpeg(Path input, Path output, long offsetSeconds) {
        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(input.toString())
                .setStartOffset(offsetSeconds, TimeUnit.SECONDS)
                .done()
                .overrideOutputFiles(true)
                .addOutput(output.toString())
                .setFrames(1)
                .done();
        try {
            new FFmpegExecutor(ffmpeg).createJob(builder).run();
        } catch (IOException e) {
            throw new IllegalStateException("ffmpeg 실행 실패: " + e.getMessage(), e);
        }
    }

    private Path createOutputFile() {
        try {
            return Files.createTempFile("thumbnail-", ".jpg");
        } catch (IOException e) {
            throw new IllegalStateException("썸네일 임시 파일 생성 실패", e);
        }
    }

    private void ensureNotEmpty(Path output) {
        try {
            if (Files.size(output) == 0) {
                throw new IllegalStateException("썸네일 추출 결과가 비어 있음");
            }
        } catch (IOException e) {
            throw new IllegalStateException("썸네일 추출 결과 확인 실패", e);
        }
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("임시 파일 삭제 실패: {}", file, e);
        }
    }
}
