package com.example.transcourse.domain.video.controller;

import com.example.transcourse.domain.video.dto.ArtifactUrlResponse;
import com.example.transcourse.domain.video.dto.VideoStatusResponse;
import com.example.transcourse.domain.video.dto.VideoUploadResponse;
import com.example.transcourse.domain.video.facade.VideoFacade;
import com.example.transcourse.domain.video.service.VideoService;
import com.example.transcourse.global.apipayload.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoFacade videoFacade;
    private final VideoService videoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<VideoUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        return CommonResponse.onSuccess(videoFacade.upload(file));
    }

    @GetMapping("/{videoId}")
    public CommonResponse<VideoStatusResponse> getStatus(@PathVariable Long videoId) {
        return CommonResponse.onSuccess(videoService.getStatus(videoId));
    }

    @GetMapping("/{videoId}/thumbnail-url")
    public CommonResponse<ArtifactUrlResponse> getThumbnailUrl(@PathVariable Long videoId) {
        return CommonResponse.onSuccess(videoFacade.thumbnailUrl(videoId));
    }

    @GetMapping(value = "/{videoId}/playlist.m3u8", produces = "application/vnd.apple.mpegurl")
    public String getHlsPlaylist(@PathVariable Long videoId) {
        return videoFacade.hlsPlaylist(videoId);
    }
}
