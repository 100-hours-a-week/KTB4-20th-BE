package com.planit.photomission.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PhotoEvaluationTest {

    @DisplayName("AI 판정 시작 시 실행 상태만 기록하고 결과는 비워둔다")
    @Test
    void startsEvaluation() {
        PhotoEvaluation evaluation = PhotoEvaluation.start(
                mock(MissionPhoto.class),
                1,
                now()
        );

        assertThat(evaluation.getExecutionStatus())
                .isEqualTo(PhotoEvaluationExecutionStatus.RUNNING);
        assertThat(evaluation.getResult()).isNull();
        assertThat(evaluation.getErrorCode()).isNull();
        assertThat(evaluation.getEvaluatedAt()).isNull();
    }

    @DisplayName("정상 AI 응답의 재시도 판정과 안내 문구를 기록한다")
    @Test
    void succeedsWithRetryResult() {
        PhotoEvaluation evaluation = runningEvaluation();
        LocalDateTime evaluatedAt = now().plusSeconds(2);

        evaluation.succeed(
                PhotoEvaluationResult.RETRY,
                null,
                new BigDecimal("75.00"),
                "[{\"name\":\"석탑\",\"matched\":true}]",
                new BigDecimal("92.00"),
                "탑이 사람에 가려졌어요.",
                evaluatedAt
        );

        assertThat(evaluation.getExecutionStatus())
                .isEqualTo(PhotoEvaluationExecutionStatus.SUCCEEDED);
        assertThat(evaluation.getResult())
                .isEqualTo(PhotoEvaluationResult.RETRY);
        assertThat(evaluation.getMatchScore())
                .isEqualByComparingTo("75.00");
        assertThat(evaluation.getRetryHint())
                .isEqualTo("탑이 사람에 가려졌어요.");
        assertThat(evaluation.getEvaluatedAt()).isEqualTo(evaluatedAt);
    }

    @DisplayName("위치 불일치는 FAIL 판정 사유로 기록한다")
    @Test
    void succeedsWithLocationMismatch() {
        PhotoEvaluation evaluation = runningEvaluation();

        evaluation.succeed(
                PhotoEvaluationResult.FAIL,
                PhotoEvaluationReason.LOCATION_MISMATCH,
                BigDecimal.ZERO,
                "[]",
                null,
                null,
                now().plusSeconds(1)
        );

        assertThat(evaluation.getResult())
                .isEqualTo(PhotoEvaluationResult.FAIL);
        assertThat(evaluation.getReason())
                .isEqualTo(PhotoEvaluationReason.LOCATION_MISMATCH);
    }

    @DisplayName("AI 호출 오류는 판정 결과 없이 실행 실패로 기록한다")
    @Test
    void failsExecutionWithoutResult() {
        PhotoEvaluation evaluation = runningEvaluation();

        evaluation.failExecution("AI_TIMEOUT", now().plusSeconds(5));

        assertThat(evaluation.getExecutionStatus())
                .isEqualTo(PhotoEvaluationExecutionStatus.FAILED);
        assertThat(evaluation.getResult()).isNull();
        assertThat(evaluation.getErrorCode()).isEqualTo("AI_TIMEOUT");
    }

    @DisplayName("정상 종료에는 반드시 판정 결과가 있어야 한다")
    @Test
    void rejectsNullResultOnSuccess() {
        PhotoEvaluation evaluation = runningEvaluation();

        assertThatThrownBy(() -> evaluation.succeed(
                null,
                null,
                BigDecimal.ZERO,
                "[]",
                null,
                null,
                now()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI 판정 결과는 필수입니다");
    }

    @DisplayName("RETRY 판정에만 재촬영 안내 문구를 저장한다")
    @Test
    void validatesRetryHint() {
        PhotoEvaluation retryEvaluation = runningEvaluation();
        PhotoEvaluation successEvaluation = runningEvaluation();

        assertThatThrownBy(() -> retryEvaluation.succeed(
                PhotoEvaluationResult.RETRY,
                null,
                new BigDecimal("75.00"),
                "[]",
                null,
                null,
                now()
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> successEvaluation.succeed(
                PhotoEvaluationResult.SUCCESS,
                null,
                new BigDecimal("95.00"),
                "[]",
                null,
                "다시 찍어주세요.",
                now()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private PhotoEvaluation runningEvaluation() {
        return PhotoEvaluation.start(mock(MissionPhoto.class), 1, now());
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 10, 8, 15, 0);
    }
}
