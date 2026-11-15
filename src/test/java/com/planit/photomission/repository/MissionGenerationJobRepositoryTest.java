package com.planit.photomission.repository;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.photomission.domain.MissionGenerationJob;
import com.planit.photomission.domain.MissionGenerationStatus;
import com.planit.repository.RegionRepository;
import com.planit.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MissionGenerationJobRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 0, 0);

    @Autowired
    private MissionGenerationJobRepository repository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MissionGenerationJob job;

    @BeforeEach
    void setUp() {
        Region region = regionRepository.findById(1L).orElseThrow();
        LocalDate tripDate = LocalDate.of(2026, 12, 1);
        Trip trip = tripRepository.save(new Trip(
                region,
                "포토미션 생성 여행",
                tripDate,
                tripDate.plusDays(2),
                (byte) 4,
                tripDate.minusDays(1).atTime(23, 59, 59)
        ));
        job = repository.saveAndFlush(MissionGenerationJob.start(trip, NOW));
    }

    @DisplayName("메시지 큐로 접수한 작업을 종료한다")
    @Test
    void finishesQueuedJob() {
        markQueued();

        assertThat(repository.finishQueued(job.getId(), MissionGenerationStatus.SUCCEEDED)).isOne();

        assertThat(statusOf(job)).isEqualTo(MissionGenerationStatus.SUCCEEDED);
    }

    @DisplayName("이미 끝났거나 기존 동기 방식으로 실행 중인 작업은 종료하지 않는다")
    @Test
    void finishesOnlyQueuedJob() {
        assertThat(repository.finishQueued(job.getId(), MissionGenerationStatus.FAILED)).isZero();
        assertThat(statusOf(job)).isEqualTo(MissionGenerationStatus.RUNNING);

        markQueued();
        repository.finishQueued(job.getId(), MissionGenerationStatus.SUCCEEDED);

        assertThat(repository.finishQueued(job.getId(), MissionGenerationStatus.FAILED)).isZero();
        assertThat(statusOf(job)).isEqualTo(MissionGenerationStatus.SUCCEEDED);
    }

    /** QUEUED로 접수하는 코드는 포토미션 생성 비동기 전환(#296)에서 추가한다. */
    private void markQueued() {
        jdbcTemplate.update("UPDATE mission_generation_jobs SET status = 'QUEUED' WHERE id = ?", job.getId());
    }

    private MissionGenerationStatus statusOf(MissionGenerationJob target) {
        return repository.findById(target.getId()).orElseThrow().getStatus();
    }
}
