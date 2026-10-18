package com.planit.trip.service;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.repository.RegionRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.PlaceDetails;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleLeg;
import com.planit.schedule.domain.ScheduleStrategy;
import com.planit.schedule.domain.ScheduleVisit;
import com.planit.schedule.dto.ScheduleConfirmRequest;
import com.planit.schedule.repository.PlaceRepository;
import com.planit.schedule.repository.ScheduleDayRepository;
import com.planit.schedule.repository.ScheduleLegRepository;
import com.planit.schedule.repository.ScheduleRepository;
import com.planit.schedule.repository.ScheduleVisitRepository;
import com.planit.schedule.service.ScheduleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UnconfirmedTripDeletionIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 확정 API는 실제 현재 시각으로 마감(시작 전날 23:50)을 검사하므로 충분히 먼 날짜를 쓴다.
    private static final LocalDate START_DATE =
            LocalDate.now(SEOUL).plusYears(70).withDayOfYear(100);
    private static final LocalDateTime DELETED_AT =
            START_DATE.minusDays(1).atTime(23, 50);

    @Autowired
    private UnconfirmedTripDeletionService deletionService;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleDayRepository dayRepository;

    @Autowired
    private ScheduleVisitRepository visitRepository;

    @Autowired
    private ScheduleLegRepository legRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> tripIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> placeIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long tripId : tripIds) {
            jdbcTemplate.update("""
                    DELETE leg FROM schedule_legs leg
                    JOIN schedule_days day ON day.id = leg.schedule_day_id
                    JOIN schedules schedule ON schedule.id = day.schedule_id
                    WHERE schedule.trip_id = ?
                    """, tripId);
            jdbcTemplate.update("""
                    DELETE visit FROM schedule_visits visit
                    JOIN schedule_days day ON day.id = visit.schedule_day_id
                    JOIN schedules schedule ON schedule.id = day.schedule_id
                    WHERE schedule.trip_id = ?
                    """, tripId);
            jdbcTemplate.update("""
                    DELETE day FROM schedule_days day
                    JOIN schedules schedule ON schedule.id = day.schedule_id
                    WHERE schedule.trip_id = ?
                    """, tripId);
            jdbcTemplate.update("DELETE FROM schedules WHERE trip_id = ?", tripId);
            jdbcTemplate.update(
                    "DELETE FROM trip_members WHERE trip_id = ?",
                    tripId
            );
            jdbcTemplate.update("DELETE FROM trips WHERE id = ?", tripId);
        }
        placeIds.forEach(placeRepository::deleteById);
        userIds.forEach(userRepository::deleteById);
    }

    @DisplayName("일정 후보를 생성하지 않은 여행과 후보를 확정하지 않은 여행을 모두 삭제하고 하위 데이터를 정리한다")
    @Test
    void deletesTripsWithoutConfirmedSchedule() {
        Fixture noCandidate = createTrip(START_DATE, 2);
        Fixture draftOnly = createTrip(START_DATE, 3);
        List<Long> draftScheduleIds = createCandidates(draftOnly.trip());
        User earlierLeftUser = createUser();
        TripMember earlierLeft = tripMemberRepository.save(
                TripMember.createMember(draftOnly.trip(), earlierLeftUser)
        );
        LocalDateTime earlierLeftAt = DELETED_AT.minusDays(3)
                .truncatedTo(ChronoUnit.SECONDS);
        transactionTemplate.executeWithoutResult(status -> tripMemberRepository
                .findById(earlierLeft.getId())
                .orElseThrow()
                .leave(earlierLeftAt));

        runScheduler();

        for (Fixture fixture : List.of(noCandidate, draftOnly)) {
            assertThat(deletedAt(fixture.trip())).isEqualTo(DELETED_AT);
            assertThat(activeMemberCount(fixture.trip())).isZero();
            assertThat(leftAts(fixture.memberIds()))
                    .containsOnly(DELETED_AT);
            assertThat(scheduleRepository.findByTripId(fixture.trip().getId()))
                    .isEmpty();
        }
        // 이미 나간 멤버십의 종료 시각과 이력은 그대로 둔다.
        assertThat(leftAts(List.of(earlierLeft.getId())))
                .containsExactly(earlierLeftAt);
        assertThat(countChildren(draftScheduleIds)).isZero();
        // 장소는 지역 단위로 공유되는 데이터라 정리 대상이 아니다.
        assertThat(placeRepository.findAllById(placeIds)).hasSize(2);
    }

    @DisplayName("ACTIVE 확정 일정이 있는 여행은 삭제하지 않는다")
    @Test
    void keepsTripWithActiveSchedule() {
        Fixture confirmed = createTrip(START_DATE, 2);
        List<Long> scheduleIds = createCandidates(confirmed.trip());
        scheduleService.confirmSchedule(
                confirmed.hostPublicId(),
                confirmed.trip().getId(),
                new ScheduleConfirmRequest(scheduleIds.get(0))
        );

        runScheduler();

        assertThat(deletedAt(confirmed.trip())).isNull();
        assertThat(activeMemberCount(confirmed.trip())).isEqualTo(2);
        assertThat(scheduleRepository.findByTripId(confirmed.trip().getId()))
                .hasSize(3);
    }

    @DisplayName("다음 날이 아닌 날짜에 시작하는 여행은 삭제하지 않는다")
    @Test
    void keepsTripsOutsideTargetDate() {
        Fixture startsToday = createTrip(START_DATE.minusDays(1), 2);
        Fixture startsLater = createTrip(START_DATE.plusDays(1), 2);
        Fixture target = createTrip(START_DATE, 2);

        assertThat(deletionService.findTargetTripIds(START_DATE))
                .contains(target.trip().getId())
                .doesNotContain(
                        startsToday.trip().getId(),
                        startsLater.trip().getId()
                );

        runScheduler();

        assertThat(deletedAt(target.trip())).isEqualTo(DELETED_AT);
        assertThat(deletedAt(startsToday.trip())).isNull();
        assertThat(deletedAt(startsLater.trip())).isNull();
    }

    @DisplayName("스케줄러를 반복 실행해도 첫 삭제 결과가 그대로 유지된다")
    @Test
    void repeatedRunsAreIdempotent() {
        Fixture fixture = createTrip(START_DATE, 2);

        runScheduler();
        new UnconfirmedTripDeletionScheduler(
                deletionService,
                fixedClock(DELETED_AT.plusMinutes(1))
        ).deleteUnconfirmedTrips();

        assertThat(deletedAt(fixture.trip())).isEqualTo(DELETED_AT);
        assertThat(leftAts(fixture.memberIds())).containsOnly(DELETED_AT);
        assertThat(deletionService.deleteIfUnconfirmed(
                fixture.trip().getId(),
                START_DATE,
                DELETED_AT.plusMinutes(2)
        )).isFalse();
        assertThat(deletedAt(fixture.trip())).isEqualTo(DELETED_AT);
    }

    @DisplayName("확정이 먼저 여행 잠금을 잡으면 스케줄러는 확정 커밋 후 다시 검사해 삭제하지 않는다")
    @Test
    void confirmationWinsWhenItLocksFirst() throws Exception {
        Fixture fixture = createTrip(START_DATE, 2);
        List<Long> scheduleIds = createCandidates(fixture.trip());
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> confirmation = executor.submit(() -> transactionTemplate
                    .executeWithoutResult(status -> {
                        scheduleService.confirmSchedule(
                                fixture.hostPublicId(),
                                fixture.trip().getId(),
                                new ScheduleConfirmRequest(scheduleIds.get(1))
                        );
                        locked.countDown();
                        await(release);
                    }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> deletion = executor.submit(() -> deletionService
                    .deleteIfUnconfirmed(
                            fixture.trip().getId(),
                            START_DATE,
                            DELETED_AT
                    ));
            assertBlocked(deletion);

            release.countDown();
            confirmation.get(10, TimeUnit.SECONDS);
            assertThat(deletion.get(10, TimeUnit.SECONDS)).isFalse();
        } finally {
            release.countDown();
        }

        assertThat(deletedAt(fixture.trip())).isNull();
        assertThat(activeMemberCount(fixture.trip())).isEqualTo(2);
        assertThat(scheduleRepository.existsByTripIdAndActiveConfirmedSlot(
                fixture.trip().getId(),
                (byte) 1
        )).isTrue();
    }

    @DisplayName("스케줄러가 먼저 여행 잠금을 잡으면 확정 요청은 삭제된 여행으로 거부된다")
    @Test
    void deletionWinsWhenItLocksFirst() throws Exception {
        Fixture fixture = createTrip(START_DATE, 2);
        List<Long> scheduleIds = createCandidates(fixture.trip());
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> deletion = executor.submit(() -> transactionTemplate
                    .execute(status -> {
                        boolean deleted = deletionService.deleteIfUnconfirmed(
                                fixture.trip().getId(),
                                START_DATE,
                                DELETED_AT
                        );
                        locked.countDown();
                        await(release);
                        return deleted;
                    }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> confirmation = executor.submit(() -> scheduleService
                    .confirmSchedule(
                            fixture.hostPublicId(),
                            fixture.trip().getId(),
                            new ScheduleConfirmRequest(scheduleIds.get(1))
                    ));
            assertBlocked(confirmation);

            release.countDown();
            assertThat(deletion.get(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> confirmation.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(BusinessException.class)
                    .cause()
                    .satisfies(cause -> assertThat(
                            ((BusinessException) cause).getErrorCode()
                    ).isEqualTo(ErrorCode.TRIP_NOT_FOUND));
        } finally {
            release.countDown();
        }

        assertThat(deletedAt(fixture.trip())).isEqualTo(DELETED_AT);
        assertThat(activeMemberCount(fixture.trip())).isZero();
        assertThat(scheduleRepository.findByTripId(fixture.trip().getId()))
                .isEmpty();
    }

    private void runScheduler() {
        new UnconfirmedTripDeletionScheduler(
                deletionService,
                fixedClock(DELETED_AT)
        ).deleteUnconfirmedTrips();
    }

    private Clock fixedClock(LocalDateTime seoulDateTime) {
        return Clock.fixed(seoulDateTime.atZone(SEOUL).toInstant(), SEOUL);
    }

    private Fixture createTrip(LocalDate startDate, int memberCount) {
        Region region = regionRepository.findById(1L).orElseThrow();
        Trip trip = tripRepository.save(new Trip(
                region,
                "삭제테스트",
                startDate,
                startDate.plusDays(1),
                (byte) 4,
                startDate.minusDays(3).atTime(23, 59, 59)
        ));
        tripIds.add(trip.getId());

        User host = createUser();
        List<Long> memberIds = new ArrayList<>();
        memberIds.add(tripMemberRepository.save(
                TripMember.createHost(trip, host)
        ).getId());
        for (int index = 1; index < memberCount; index++) {
            memberIds.add(tripMemberRepository.save(
                    TripMember.createMember(trip, createUser())
            ).getId());
        }
        return new Fixture(trip, host.getPublicId().toString(), memberIds);
    }

    private User createUser() {
        User user = userRepository.save(new User(
                null,
                UUID.randomUUID(),
                "삭제테스트사용자"
        ));
        userIds.add(user.getId());
        return user;
    }

    /** 세 전략 후보와 각 후보의 Day·Visit·Leg를 저장하고 일정 ID를 반환한다. */
    private List<Long> createCandidates(Trip trip) {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now(SEOUL)
                    .truncatedTo(ChronoUnit.MICROS);
            Place first = savePlace(trip.getRegion(), now);
            Place second = savePlace(trip.getRegion(), now);
            List<Long> scheduleIds = new ArrayList<>();
            for (ScheduleStrategy strategy : ScheduleStrategy.values()) {
                Schedule schedule = scheduleRepository.save(new Schedule(
                        trip,
                        strategy,
                        strategy == ScheduleStrategy.SHORTEST ? null : 80,
                        now
                ));
                ScheduleDay day = dayRepository.save(new ScheduleDay(
                        schedule,
                        (byte) 1,
                        trip.getStartDate()
                ));
                ScheduleVisit from = visitRepository.save(
                        new ScheduleVisit(day, first, 1, "추천", now)
                );
                ScheduleVisit to = visitRepository.save(
                        new ScheduleVisit(day, second, 2, "추천", now)
                );
                legRepository.save(new ScheduleLeg(day, from, to, 1, 500));
                scheduleIds.add(schedule.getId());
            }
            return scheduleIds;
        });
    }

    private Place savePlace(Region region, LocalDateTime now) {
        Place place = placeRepository.save(new Place(
                region,
                new PlaceDetails(
                        "delete-test-" + UUID.randomUUID(),
                        "테스트 장소",
                        "관광명소",
                        null,
                        null,
                        new BigDecimal("126.9780000"),
                        new BigDecimal("37.5665000"),
                        null,
                        null
                ),
                now
        ));
        placeIds.add(place.getId());
        return place;
    }

    private LocalDateTime deletedAt(Trip trip) {
        return jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM trips WHERE id = ?",
                LocalDateTime.class,
                trip.getId()
        );
    }

    private Integer activeMemberCount(Trip trip) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM trip_members
                WHERE trip_id = ? AND active_slot = 1 AND left_at IS NULL
                """, Integer.class, trip.getId());
    }

    private List<LocalDateTime> leftAts(List<Long> memberIds) {
        return memberIds.stream()
                .map(memberId -> jdbcTemplate.queryForObject(
                        "SELECT left_at FROM trip_members WHERE id = ?",
                        LocalDateTime.class,
                        memberId
                ))
                .toList();
    }

    private Integer countChildren(List<Long> scheduleIds) {
        String ids = String.join(
                ",",
                scheduleIds.stream().map(String::valueOf).toList()
        );
        return jdbcTemplate.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM schedule_days
                   WHERE schedule_id IN (%1$s))
                  + (SELECT COUNT(*) FROM schedule_visits visit
                     JOIN schedule_days day ON day.id = visit.schedule_day_id
                     WHERE day.schedule_id IN (%1$s))
                  + (SELECT COUNT(*) FROM schedule_legs leg
                     JOIN schedule_days day ON day.id = leg.schedule_day_id
                     WHERE day.schedule_id IN (%1$s))
                  + (SELECT COUNT(*) FROM schedules WHERE id IN (%1$s))
                """.formatted(ids), Integer.class);
    }

    private static void assertBlocked(Future<?> future) throws Exception {
        assertThatThrownBy(() -> future.get(500, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("release timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(
            Trip trip,
            String hostPublicId,
            List<Long> memberIds
    ) {
    }
}
