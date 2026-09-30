package com.planit.global.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiResponseTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @DisplayName("성공 응답은 코드와 메시지, 데이터를 포함하고 오류는 포함하지 않는다")
    @Test
    void successResponseContainsCodeMessageAndDataWithoutErrors() throws Exception {
        ApiResponse<Map<String, String>> response = ApiResponse.success(
                "RESOURCE_RETRIEVED",
                "리소스를 조회했습니다.",
                Map.of("resourceId", "123")
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("code").asText()).isEqualTo("RESOURCE_RETRIEVED");
        assertThat(json.get("message").asText()).isEqualTo("리소스를 조회했습니다.");
        assertThat(json.get("data").get("resourceId").asText()).isEqualTo("123");
        assertThat(json.has("errors")).isFalse();
    }

    @DisplayName("일반 오류 응답은 데이터와 검증 오류를 포함하지 않는다")
    @Test
    void errorResponseContainsNullDataWithoutErrors() {
        ApiResponse<Void> response = ApiResponse.error(
                "RESOURCE_NOT_FOUND",
                "리소스를 찾을 수 없습니다."
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(json.get("message").asText()).isEqualTo("리소스를 찾을 수 없습니다.");
        assertThat(json.get("data").isNull()).isTrue();
        assertThat(json.has("errors")).isFalse();
    }

    @DisplayName("검증 오류 응답은 필드와 오류 사유를 포함한다")
    @Test
    void validationErrorResponseContainsFieldAndReason() {
        ApiResponse<Void> response = ApiResponse.validationError(
                "INVALID_REQUEST",
                "요청 값을 확인해 주세요.",
                List.of(new ApiFieldError("fieldName", ValidationErrorReason.REQUIRED))
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("data").isNull()).isTrue();
        assertThat(json.get("errors").get(0).get("field").asText()).isEqualTo("fieldName");
        assertThat(json.get("errors").get(0).get("reason").asText()).isEqualTo("REQUIRED");
    }

    @DisplayName(
            "검증 오류 응답은 원본 목록의 변경에 영향받지 않고 직접 수정할 수도 없다"
    )
    @Test
    void validationErrorsAreDefensivelyCopied() {
        List<ApiFieldError> errors = new ArrayList<>();
        errors.add(new ApiFieldError("fieldName", ValidationErrorReason.REQUIRED));

        ApiResponse<Void> response = ApiResponse.validationError(
                "INVALID_REQUEST",
                "요청 값을 확인해 주세요.",
                errors
        );
        errors.clear();

        assertThat(response.errors()).hasSize(1);
        assertThatThrownBy(() -> response.errors().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @DisplayName("빈 검증 오류 목록으로 응답을 생성할 수 없다")
    @Test
    void validationErrorRejectsEmptyErrors() {
        assertThatThrownBy(() -> ApiResponse.validationError(
                "INVALID_REQUEST",
                "요청 값을 확인해 주세요.",
                List.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
