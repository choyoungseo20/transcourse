package com.example.transcourse.domain.job.messaging;

import com.example.transcourse.domain.job.entity.JobType;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

// 영상 ID를 담는 레코드 값
// 대상 유형 헤더가 없으면 모든 유형의 그룹이 처리
// 헤더가 있으면 그 유형의 그룹만 처리
final class JobRequest {

    static final String TARGET_TYPE_HEADER = "target-job-type";

    private JobRequest() {
    }

    static Long videoId(ConsumerRecord<String, String> record) {
        return Long.valueOf(record.value());
    }

    static boolean targets(ConsumerRecord<String, String> record, JobType type) {
        Header header = record.headers().lastHeader(TARGET_TYPE_HEADER);
        return header == null || type.name().equals(new String(header.value(), StandardCharsets.UTF_8));
    }
}
