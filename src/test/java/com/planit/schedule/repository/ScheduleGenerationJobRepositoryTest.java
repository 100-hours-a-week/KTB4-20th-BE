package com.planit.schedule.repository;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.repository.RegionRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.domain.ScheduleGenerationJob;
import com.planit.schedule.domain.ScheduleGenerationJobStatus;
import com.planit.schedule.service.ScheduleGenerationMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ScheduleGenerationJobRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 10, 0);

    @Autowired
    private ScheduleGenerationJobRepository repository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Trip trip;
    private TripMember member;

    @BeforeEach
    void setUp() {
        Region region = regionRepository.findById(1L).orElseThrow();
        LocalDate tripDate = LocalDate.of(2026, 12, 1);
        trip = tripRepository.save(new Trip(
                region,
                "경주 여행",
                tripDate,
                tripDate.plusDays(2),
                (byte) 4,
                tripDate.minusDays(1).atTime(23, 59, 59)
        ));
        User user = userRepository.save(new User(null, UUID.randomUUID(), "일정생성사용자"));
        member = tripMemberRepository.save(TripMember.createMember(trip, user));
    }

    @DisplayName("접수한 작업을 QUEUED, active_slot 1로 저장하고 요청 시각을 입력 기준 시각으로 쓴다")
    @Test
    void savesQueuedJob() {
        UUID idempotencyKey = UUID.randomUUID();
        ScheduleGenerationJob saved = repository.saveAndFlush(ScheduleGenerationJob.queued(
                trip, member, ScheduleGenerationMode.GENERAL_REGENERATION, idempotencyKey, NOW
        ));

        ScheduleGenerationJob found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ScheduleGenerationJobStatus.QUEUED);
        assertThat(found.getJobType()).isEqualTo(ScheduleGenerationMode.GENERAL_REGENERATION);
        assertThat(found.getActiveSlot()).isEqualTo((byte) 1);
        assertThat(found.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(found.getInputCutoffAt()).isEqualTo(NOW);
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
        assertThat(found.getFinishedAt()).isNull();
        assertThat(found.getErrorCode()).isNull();
    }

    @DisplayName("같은 여행에 진행 중인 작업은 하나만 저장할 수 있다")
    @Test
    void rejectsSecondActiveJobForSameTrip() {
        repository.saveAndFlush(queued(UUID.randomUUID()));

        assertThatThrownBy(() -> repository.saveAndFlush(queued(UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("끝난 작업은 active_slot이 NULL이라 같은 여행에 새 작업을 받을 수 있다")
    @Test
    void allowsNewJobAfterPreviousJobFinished() {
        ScheduleGenerationJob finished = repository.saveAndFlush(queued(UUID.randomUUID()));
        jdbcTemplate.update(
                "UPDATE schedule_generation_jobs SET status = 'FAILED', active_slot = NULL WHERE id = ?",
                finished.getId()
        );

        ScheduleGenerationJob next = repository.saveAndFlush(queued(UUID.randomUUID()));

        assertThat(next.getId()).isNotEqualTo(finished.getId());
    }

    @DisplayName("같은 멱등키로는 두 번 저장할 수 없다")
    @Test
    void rejectsDuplicateIdempotencyKey() {
        UUID idempotencyKey = UUID.randomUUID();
        ScheduleGenerationJob finished = repository.saveAndFlush(queued(idempotencyKey));
        jdbcTemplate.update(
                "UPDATE schedule_generation_jobs SET status = 'FAILED', active_slot = NULL WHERE id = ?",
                finished.getId()
        );

        assertThatThrownBy(() -> repository.saveAndFlush(queued(idempotencyKey)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("멱등키가 없으면 만들 수 없다")
    @Test
    void rejectsMissingIdempotencyKey() {
        assertThatThrownBy(() -> queued(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("진행 중인 작업을 종료하면 active_slot을 비우고 종료 시각과 실패 원인을 남긴다")
    @Test
    void finishesQueuedJob() {
        ScheduleGenerationJob saved = repository.saveAndFlush(queued(UUID.randomUUID()));
        LocalDateTime finishedAt = NOW.plusMinutes(1);

        assertThat(repository.finishQueued(
                saved.getId(), ScheduleGenerationJobStatus.FAILED, "DELIVERY_LIMIT", finishedAt
        )).isOne();

        ScheduleGenerationJob found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ScheduleGenerationJobStatus.FAILED);
        assertThat(found.getActiveSlot()).isNull();
        assertThat(found.getFinishedAt()).isEqualTo(finishedAt);
        assertThat(found.getErrorCode()).isEqualTo("DELIVERY_LIMIT");
    }

    @DisplayName("이미 끝난 작업은 다시 종료하지 않는다")
    @Test
    void doesNotFinishTwice() {
        ScheduleGenerationJob saved = repository.saveAndFlush(queued(UUID.randomUUID()));
        LocalDateTime finishedAt = NOW.plusMinutes(1);
        repository.finishQueued(saved.getId(), ScheduleGenerationJobStatus.SUCCEEDED, null, finishedAt);

        assertThat(repository.finishQueued(
                saved.getId(), ScheduleGenerationJobStatus.FAILED, "STALE", finishedAt.plusMinutes(30)
        )).isZero();

        ScheduleGenerationJob found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ScheduleGenerationJobStatus.SUCCEEDED);
        assertThat(found.getFinishedAt()).isEqualTo(finishedAt);
        assertThat(found.getErrorCode()).isNull();
    }

    @DisplayName("Idempotency-Key로 이미 접수한 작업을 찾는다")
    @Test
    void findsJobByIdempotencyKey() {
        UUID idempotencyKey = UUID.randomUUID();
        ScheduleGenerationJob saved = repository.saveAndFlush(queued(idempotencyKey));

        assertThat(repository.findByIdempotencyKey(idempotencyKey))
                .map(ScheduleGenerationJob::getId)
                .contains(saved.getId());
        assertThat(repository.findByIdempotencyKey(UUID.randomUUID())).isEmpty();
    }

    private ScheduleGenerationJob queued(UUID idempotencyKey) {
        return ScheduleGenerationJob.queued(
                trip, member, ScheduleGenerationMode.INITIAL, idempotencyKey, NOW
        );
    }
}
