package com.example.transcourse.domain.video.exception;

import com.example.transcourse.global.apipayload.ErrorStatus;
import com.example.transcourse.global.exception.GeneralException;

public class VideoNotFoundException extends GeneralException {

    public VideoNotFoundException(Long videoId) {
        super(ErrorStatus.VIDEO_NOT_FOUND, "존재하지 않는 영상: id=" + videoId);
    }
}
