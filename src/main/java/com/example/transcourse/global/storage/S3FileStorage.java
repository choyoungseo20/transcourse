package com.example.transcourse.global.storage;

import com.example.transcourse.global.apipayload.ErrorStatus;
import com.example.transcourse.global.config.StorageConfig.StorageProperties;
import com.example.transcourse.global.exception.GeneralException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3FileStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties props;

    @PostConstruct
    void ensureBucket() {
        try {
            s3.headBucket(b -> b.bucket(props.bucket()));
        } catch (NoSuchBucketException e) {
            s3.createBucket(b -> b.bucket(props.bucket()));
        }
    }

    public String save(MultipartFile file, String key) {
        try (InputStream in = file.getInputStream()) {
            s3.putObject(
                    b -> b.bucket(props.bucket()).key(key).contentLength(file.getSize()),
                    RequestBody.fromInputStream(in, file.getSize()));
            return key;
        } catch (IOException | SdkException e) {
            throw new GeneralException(ErrorStatus.STORAGE_SAVE_FAILURE, "파일 저장 실패: key=" + key, e);
        }
    }

    public void saveFile(Path file, String key) {
        try {
            s3.putObject(b -> b.bucket(props.bucket()).key(key), RequestBody.fromFile(file));
        } catch (SdkException e) {
            throw new IllegalStateException("산출물 업로드 실패: key=" + key, e);
        }
    }

    public void saveDirectory(Path dir, String keyPrefix) {
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .forEach(file -> saveFile(file, keyPrefix + file.getFileName()));
        } catch (IOException e) {
            throw new IllegalStateException("산출물 디렉터리 업로드 실패: prefix=" + keyPrefix, e);
        }
    }

    public Path downloadToTemp(String key) {
        Path target;
        try {
            target = Files.createTempFile("video-original-", null);
            Files.delete(target);
        } catch (IOException e) {
            throw new IllegalStateException("임시 파일 생성 실패: key=" + key, e);
        }
        try {
            s3.getObject(b -> b.bucket(props.bucket()).key(key), target);
            return target;
        } catch (SdkException e) {
            deletePartialFile(target);
            throw new IllegalStateException("원본 다운로드 실패: key=" + key, e);
        }
    }

    private void deletePartialFile(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("부분 파일 삭제 실패: {}", target, e);
        }
    }

    public String presignGet(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .build();
        return presigner.presignGetObject(b -> b
                        .signatureDuration(props.presignTtl())
                        .getObjectRequest(request))
                .url()
                .toString();
    }

    public String readUtf8(String key) {
        try {
            return s3.getObjectAsBytes(b -> b.bucket(props.bucket()).key(key)).asUtf8String();
        } catch (SdkException e) {
            throw new GeneralException(ErrorStatus.STORAGE_READ_FAILURE, "파일 조회 실패: key=" + key, e);
        }
    }

    public void deleteQuietly(String key) {
        try {
            s3.deleteObject(b -> b.bucket(props.bucket()).key(key));
        } catch (SdkException e) {
            log.warn("보상 삭제 실패 (고아 객체로 남음): key={}", key, e);
        }
    }
}
