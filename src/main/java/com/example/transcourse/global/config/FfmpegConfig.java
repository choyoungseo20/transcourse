package com.example.transcourse.global.config;

import java.io.IOException;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FfmpegConfig {

    @Bean
    public FFprobe ffprobe(@Value("${app.ffmpeg.ffprobe-path}") String ffprobePath) throws IOException {
        return new FFprobe(ffprobePath);
    }

    @Bean
    public FFmpeg ffmpeg(@Value("${app.ffmpeg.ffmpeg-path}") String ffmpegPath) throws IOException {
        return new FFmpeg(ffmpegPath);
    }
}
