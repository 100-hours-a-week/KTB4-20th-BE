package com.planit.mq.intake;

import com.planit.global.response.ApiResponse;
import com.planit.mq.job.AiJobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiJobAcceptedResponseTest {

    @DisplayName("202와 AI_JOB_ACCEPTED 코드로 작업 종류, 문자열 작업 ID, QUEUED 상태를 응답한다")
    @Test
    void respondsAccepted() {
        ResponseEntity<ApiResponse<AiJobAcceptedResponse>> response =
                AiJobAcceptedResponse.accepted(AiJobType.SCHEDULE_GENERATE, 123L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AI_JOB_ACCEPTED");
        assertThat(response.getBody().message()).isEqualTo("AI 작업 요청이 접수되었습니다.");
        assertThat(response.getBody().data())
                .isEqualTo(new AiJobAcceptedResponse(AiJobType.SCHEDULE_GENERATE, "123", "QUEUED"));
    }

    @DisplayName("문서의 응답 형식 그대로 JSON으로 바뀐다")
    @Test
    void serializesAsDocumented() {
        String json = JsonMapper.builder().build().writeValueAsString(
                AiJobAcceptedResponse.accepted(AiJobType.PHOTO_JUDGE, 9_007_199_254_740_993L).getBody()
        );

        assertThat(json).isEqualTo("""
                {"code":"AI_JOB_ACCEPTED","message":"AI 작업 요청이 접수되었습니다.",\
                "data":{"jobType":"PHOTO_JUDGE","jobId":"9007199254740993","status":"QUEUED"}}""");
    }

    @DisplayName("작업 ID가 없으면 응답을 만들지 않는다")
    @Test
    void rejectsMissingJobId() {
        assertThatThrownBy(() -> AiJobAcceptedResponse.queued(AiJobType.SCHEDULE_GENERATE, null))
                .isInstanceOf(NullPointerException.class);
    }
}
