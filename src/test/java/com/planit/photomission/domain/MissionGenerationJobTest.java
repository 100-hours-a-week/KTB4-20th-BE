package com.planit.photomission.domain;

import com.planit.domain.Trip;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class MissionGenerationJobTest {

    private static final LocalDateTime FIRST_ATTEMPT_AT =
            LocalDateTime.of(2026, 10, 14, 15, 0);

    @DisplayName("생성 작업을 시작하면 첫 번째 실행 시각과 상태를 기록한다")
    @Test
    void startsGeneration() {
        MissionGenerationJob job = MissionGenerationJob.start(
                mock(Trip.class),
                FIRST_ATTEMPT_AT
        );

        assertThat(job.getStatus())
                .isEqualTo(MissionGenerationStatus.RUNNING);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getLastAttemptedAt()).isEqualTo(FIRST_ATTEMPT_AT);
    }

    @DisplayName("실패한 생성 작업을 재시도하면 실행 횟수와 시각을 갱신한다")
    @Test
    void retriesFailedGeneration() {
        MissionGenerationJob job = runningJob();
        LocalDateTime retriedAt = FIRST_ATTEMPT_AT.plusMinutes(1);

        job.fail();
        job.retry(retriedAt);

        assertThat(job.getStatus())
                .isEqualTo(MissionGenerationStatus.RUNNING);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getLastAttemptedAt()).isEqualTo(retriedAt);
    }

    @DisplayName("오래 실행 중인 생성 작업을 다시 시도한다")
    @Test
    void retriesStaleRunningGeneration() {
        MissionGenerationJob job = runningJob();
        LocalDateTime retriedAt = FIRST_ATTEMPT_AT.plusMinutes(5);

        job.retryStale(retriedAt);

        assertThat(job.getStatus())
                .isEqualTo(MissionGenerationStatus.RUNNING);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getLastAttemptedAt()).isEqualTo(retriedAt);
    }

    @DisplayName("아직 실행 제한 시간이 지나지 않은 작업은 다시 시도하지 않는다")
    @Test
    void rejectsRetryForRunningGenerationBeforeTimeout() {
        MissionGenerationJob job = runningJob();

        assertThatThrownBy(() -> job.retryStale(
                FIRST_ATTEMPT_AT.plusMinutes(4)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("아직 실행 중인 포토미션 생성 작업입니다");
    }

    @DisplayName("생성 작업은 시도 횟수와 관계없이 다시 시도할 수 있다")
    @Test
    void retriesGenerationBeyondThirdAttempt() {
        MissionGenerationJob job = runningJob();
        job.fail();
        job.retry(FIRST_ATTEMPT_AT.plusMinutes(1));
        job.fail();
        job.retry(FIRST_ATTEMPT_AT.plusMinutes(2));
        job.fail();
        job.retry(FIRST_ATTEMPT_AT.plusMinutes(3));

        assertThat(job.getStatus())
                .isEqualTo(MissionGenerationStatus.RUNNING);
        assertThat(job.getAttemptCount()).isEqualTo(4);
    }

    @DisplayName("진행 중인 생성 작업을 완료 상태로 변경한다")
    @Test
    void succeedsGeneration() {
        MissionGenerationJob job = runningJob();

        job.succeed();

        assertThat(job.getStatus())
                .isEqualTo(MissionGenerationStatus.SUCCEEDED);
    }

    private MissionGenerationJob runningJob() {
        return MissionGenerationJob.start(
                mock(Trip.class),
                FIRST_ATTEMPT_AT
        );
    }
}
