package com.planit.mq.intake;

import com.planit.global.response.ApiResponse;
import com.planit.mq.job.AiJobType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Objects;

/**
 * 메시지 큐로 접수한 AI 작업의 202 응답 `data`. 결과는 알림과 작업 상태 조회로 받는다.
 *
 * @param jobId 프론트가 숫자 정밀도를 잃지 않도록 문자열로 준다
 */
public record AiJobAcceptedResponse(
        AiJobType jobType,
        String jobId,
        String status
) {

    public static final String CODE = "AI_JOB_ACCEPTED";
    public static final String MESSAGE = "AI 작업 요청이 접수되었습니다.";
    private static final String QUEUED = "QUEUED";

    public AiJobAcceptedResponse {
        Objects.requireNonNull(jobType, "작업 종류는 필수입니다.");
        Objects.requireNonNull(jobId, "작업 ID는 필수입니다.");
        Objects.requireNonNull(status, "작업 상태는 필수입니다.");
    }

    public static AiJobAcceptedResponse queued(AiJobType jobType, Long jobId) {
        return new AiJobAcceptedResponse(jobType, String.valueOf(Objects.requireNonNull(jobId, "작업 ID는 필수입니다.")), QUEUED);
    }

    /** 컨트롤러가 그대로 돌려주는 202 응답 */
    public static ResponseEntity<ApiResponse<AiJobAcceptedResponse>> accepted(AiJobType jobType, Long jobId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(CODE, MESSAGE, queued(jobType, jobId)));
    }
}
