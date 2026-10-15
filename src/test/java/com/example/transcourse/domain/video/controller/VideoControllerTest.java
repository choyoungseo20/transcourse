package com.example.transcourse.domain.video.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.transcourse.domain.video.exception.VideoNotFoundException;
import com.example.transcourse.domain.video.facade.VideoFacade;
import com.example.transcourse.domain.video.service.VideoService;
import com.example.transcourse.global.apipayload.ErrorStatus;
import com.example.transcourse.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VideoController.class)
class VideoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VideoFacade videoFacade;

    @MockitoBean
    private VideoService videoService;

    @Test
    void 존재하지_않는_영상의_상태_조회는_404와_VIDEO4002를_반환한다() throws Exception {
        given(videoService.getStatus(999L)).willThrow(new VideoNotFoundException(999L));

        mockMvc.perform(get("/videos/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VIDEO4002"));
    }

    @Test
    void 산출물이_준비되지_않았으면_409와_VIDEO4003을_반환한다() throws Exception {
        given(videoFacade.thumbnailUrl(1L))
                .willThrow(new GeneralException(ErrorStatus.VIDEO_ARTIFACT_NOT_READY));

        mockMvc.perform(get("/videos/1/thumbnail-url"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIDEO4003"));
    }
}
