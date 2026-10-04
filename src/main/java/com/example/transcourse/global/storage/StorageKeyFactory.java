package com.example.transcourse.global.storage;

import java.util.UUID;
import org.springframework.stereotype.Component;

// videos/{uuid}/ 아래에 모이는 영상 하나의 모든 객체
@Component
public class StorageKeyFactory {

    // 프로세서의 로컬 산출물 파일명과 키의 일치에도 사용
    public static final String HLS_PLAYLIST_NAME = "playlist.m3u8";

    private static final String ORIGINAL_KEY_FORMAT = "videos/%s/original";
    private static final String THUMBNAIL_KEY_NAME = "thumbnail.jpg";
    private static final String HLS_DIR_NAME = "hls/";

    public String originalKey() {
        return ORIGINAL_KEY_FORMAT.formatted(UUID.randomUUID());
    }

    public String thumbnailKey(String originalKey) {
        return directory(originalKey) + THUMBNAIL_KEY_NAME;
    }

    public String hlsPrefix(String originalKey) {
        return directory(originalKey) + HLS_DIR_NAME;
    }

    public String hlsPlaylistKey(String originalKey) {
        return hlsPrefix(originalKey) + HLS_PLAYLIST_NAME;
    }

    private String directory(String originalKey) {
        return originalKey.substring(0, originalKey.lastIndexOf('/') + 1);
    }
}
