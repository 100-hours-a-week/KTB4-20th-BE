package com.planit.photomission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "photo_evaluations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_photo_evaluations_photo_attempt",
                columnNames = {"mission_photo_id", "attempt_no"}
        )
)
public class PhotoEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_photo_id", nullable = false)
    private MissionPhoto missionPhoto;

    @Column(name = "attempt_no", nullable = false)
    private byte attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 20)
    private PhotoEvaluationExecutionStatus executionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 20)
    private PhotoEvaluationResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 40)
    private PhotoEvaluationReason reason;

    @Column(name = "match_score", precision = 5, scale = 2)
    private BigDecimal matchScore;

    @Column(name = "detected_labels_json", columnDefinition = "json")
    private String detectedLabelsJson;

    @Column(name = "landmark_confidence", precision = 5, scale = 2)
    private BigDecimal landmarkConfidence;

    @Column(name = "retry_hint", length = 1000)
    private String retryHint;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    protected PhotoEvaluation() {
    }

    public static PhotoEvaluation start(
            MissionPhoto missionPhoto,
            int attemptNo,
            LocalDateTime requestedAt
    ) {
        if (attemptNo < 1 || attemptNo > 3) {
            throw new IllegalArgumentException(
                    "사진 판정 시도 번호는 1부터 3까지입니다"
            );
        }

        PhotoEvaluation evaluation = new PhotoEvaluation();
        evaluation.missionPhoto = missionPhoto;
        evaluation.attemptNo = (byte) attemptNo;
        evaluation.executionStatus =
                PhotoEvaluationExecutionStatus.RUNNING;
        evaluation.requestedAt = requestedAt;
        return evaluation;
    }

    public void succeed(
            PhotoEvaluationResult result,
            PhotoEvaluationReason reason,
            BigDecimal matchScore,
            String detectedLabelsJson,
            BigDecimal landmarkConfidence,
            String retryHint,
            LocalDateTime evaluatedAt
    ) {
        requireRunning();
        validateResult(result);
        validateScore(matchScore);
        validateDetectedLabels(detectedLabelsJson);
        validateReason(result, reason);
        validateLandmarkConfidence(landmarkConfidence);
        validateRetryHint(result, retryHint);

        executionStatus = PhotoEvaluationExecutionStatus.SUCCEEDED;
        this.result = result;
        this.reason = reason;
        this.matchScore = matchScore;
        this.detectedLabelsJson = detectedLabelsJson;
        this.landmarkConfidence = landmarkConfidence;
        this.retryHint = retryHint;
        this.evaluatedAt = evaluatedAt;
    }

    public void failExecution(
            String errorCode,
            LocalDateTime failedAt
    ) {
        requireRunning();
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("AI 실행 오류 코드는 필수입니다");
        }

        executionStatus = PhotoEvaluationExecutionStatus.FAILED;
        this.errorCode = errorCode;
        evaluatedAt = failedAt;
    }

    private void validateResult(PhotoEvaluationResult result) {
        if (result == null) {
            throw new IllegalArgumentException("AI 판정 결과는 필수입니다");
        }
    }

    private void validateScore(BigDecimal matchScore) {
        if (matchScore == null
                || matchScore.compareTo(BigDecimal.ZERO) < 0
                || matchScore.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "미션 수행 점수는 0부터 100까지입니다"
            );
        }
    }

    private void validateDetectedLabels(String detectedLabelsJson) {
        if (detectedLabelsJson == null) {
            throw new IllegalArgumentException("인식 항목은 필수입니다");
        }
    }

    private void validateReason(
            PhotoEvaluationResult result,
            PhotoEvaluationReason reason
    ) {
        if (reason != null && result != PhotoEvaluationResult.FAIL) {
            throw new IllegalArgumentException(
                    "실패 판정에만 실패 사유를 저장할 수 있습니다"
            );
        }
    }

    private void validateLandmarkConfidence(BigDecimal confidence) {
        if (confidence == null) {
            return;
        }
        if (confidence.compareTo(BigDecimal.valueOf(60)) < 0
                || confidence.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "랜드마크 신뢰도는 60 이상 100 이하만 저장합니다"
            );
        }
    }

    private void validateRetryHint(
            PhotoEvaluationResult result,
            String retryHint
    ) {
        if (result == PhotoEvaluationResult.RETRY
                && (retryHint == null || retryHint.isBlank())) {
            throw new IllegalArgumentException(
                    "RETRY 판정에는 재촬영 안내가 필요합니다"
            );
        }
        if (result != PhotoEvaluationResult.RETRY && retryHint != null) {
            throw new IllegalArgumentException(
                    "RETRY 판정이 아니면 재촬영 안내를 저장할 수 없습니다"
            );
        }
    }

    private void requireRunning() {
        if (executionStatus != PhotoEvaluationExecutionStatus.RUNNING) {
            throw new IllegalStateException("진행 중인 AI 판정만 종료할 수 있습니다");
        }
    }

    public Long getId() {
        return id;
    }

    public MissionPhoto getMissionPhoto() {
        return missionPhoto;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public PhotoEvaluationExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public PhotoEvaluationResult getResult() {
        return result;
    }

    public PhotoEvaluationReason getReason() {
        return reason;
    }

    public BigDecimal getMatchScore() {
        return matchScore;
    }

    public String getDetectedLabelsJson() {
        return detectedLabelsJson;
    }

    public BigDecimal getLandmarkConfidence() {
        return landmarkConfidence;
    }

    public String getRetryHint() {
        return retryHint;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }
}
