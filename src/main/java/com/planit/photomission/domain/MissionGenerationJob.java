package com.planit.photomission.domain;

import com.planit.domain.Trip;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.time.Duration;

@Entity
@Table(
        name = "mission_generation_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mission_generation_jobs_trip",
                columnNames = "trip_id"
        )
)
public class MissionGenerationJob {

    private static final Duration RUNNING_TIMEOUT = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MissionGenerationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private byte attemptCount;

    @Column(name = "last_attempted_at", nullable = false)
    private LocalDateTime lastAttemptedAt;

    protected MissionGenerationJob() {
    }

    public static MissionGenerationJob start(
            Trip trip,
            LocalDateTime attemptedAt
    ) {
        MissionGenerationJob job = new MissionGenerationJob();
        job.trip = trip;
        job.status = MissionGenerationStatus.RUNNING;
        job.attemptCount = 1;
        job.lastAttemptedAt = attemptedAt;
        return job;
    }

    public void fail() {
        requireRunning();
        status = MissionGenerationStatus.FAILED;
    }

    public void retry(LocalDateTime attemptedAt) {
        if (status != MissionGenerationStatus.FAILED) {
            throw new IllegalStateException(
                    "실패한 포토미션 생성 작업만 재시도할 수 있습니다"
            );
        }
        restart(attemptedAt);
    }

    public void retryStale(LocalDateTime attemptedAt) {
        requireRunning();
        if (lastAttemptedAt.isAfter(staleBefore(attemptedAt))) {
            throw new IllegalStateException(
                    "아직 실행 중인 포토미션 생성 작업입니다"
            );
        }
        restart(attemptedAt);
    }

    public static LocalDateTime staleBefore(LocalDateTime now) {
        return now.minus(RUNNING_TIMEOUT);
    }

    private void restart(LocalDateTime attemptedAt) {
        attemptCount++;
        lastAttemptedAt = attemptedAt;
        status = MissionGenerationStatus.RUNNING;
    }

    public void succeed() {
        requireRunning();
        status = MissionGenerationStatus.SUCCEEDED;
    }

    private void requireRunning() {
        if (status != MissionGenerationStatus.RUNNING) {
            throw new IllegalStateException(
                    "진행 중인 포토미션 생성 작업만 종료할 수 있습니다"
            );
        }
    }

    public Long getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public MissionGenerationStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public LocalDateTime getLastAttemptedAt() {
        return lastAttemptedAt;
    }
}
