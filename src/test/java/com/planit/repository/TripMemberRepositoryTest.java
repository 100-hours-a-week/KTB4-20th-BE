package com.planit.repository;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.trip.dto.TripListResponse;
import com.planit.trip.service.TripService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class TripMemberRepositoryTest {

    private static final LocalDate TRIP_DATE =
            LocalDate.of(2026, 12, 1);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private TripService tripService;

    @DisplayName("여행 기간의 시작일과 종료일이 겹치면 중복으로 계산한다")
    @Test
    void countsTripOverlappingPeriodBoundaries() {
        User user = createUser();
        Trip trip = createTrip();
        tripMemberRepository.save(TripMember.createMember(trip, user));

        assertThat(tripMemberRepository.countActiveTripsOverlapping(
                user, TRIP_DATE.minusDays(1), TRIP_DATE
        )).isEqualTo(1);
        assertThat(tripMemberRepository.countActiveTripsOverlapping(
                user, TRIP_DATE.plusDays(2), TRIP_DATE.plusDays(3)
        )).isEqualTo(1);
        assertThat(tripMemberRepository.countActiveTripsOverlapping(
                user, TRIP_DATE.plusDays(3), TRIP_DATE.plusDays(4)
        )).isZero();
    }

    @DisplayName("초대받은 여행을 제외하고 일정이 겹치는 여행을 조회한다")
    @Test
    void findsOverlappingTripExceptInvitationTrip() {
        User user = createUser();
        Trip invitationTrip = createTrip("부산 여행", TRIP_DATE);
        Trip overlappingTrip = createTrip("경주 여행", TRIP_DATE);
        Trip differentDateTrip = createTrip(
                "제주 여행",
                TRIP_DATE.plusDays(3)
        );
        TripMember overlappingMembership =
                TripMember.createMember(overlappingTrip, user);

        tripMemberRepository.saveAll(List.of(
                TripMember.createMember(invitationTrip, user),
                overlappingMembership,
                TripMember.createMember(differentDateTrip, user)
        ));

        List<TripMember> result =
                tripMemberRepository.findActiveTripsOverlappingExcept(
                        user,
                        TRIP_DATE.plusDays(1),
                        TRIP_DATE.plusDays(1),
                        invitationTrip.getId()
                );

        assertThat(result).containsExactly(overlappingMembership);
    }

    @DisplayName("생성할 여행과 기간이 겹치는 여행을 조회한다")
    @Test
    void findsTripOverlappingCreationPeriod() {
        User user = createUser();
        Trip overlappingTrip = createTrip("경주 여행", TRIP_DATE);
        Trip differentDateTrip = createTrip(
                "제주 여행",
                TRIP_DATE.plusDays(3)
        );
        TripMember overlappingMembership =
                TripMember.createMember(overlappingTrip, user);

        tripMemberRepository.saveAll(List.of(
                overlappingMembership,
                TripMember.createMember(differentDateTrip, user)
        ));

        List<TripMember> result =
                tripMemberRepository.findActiveTripsOverlapping(
                        user,
                        TRIP_DATE.minusDays(1),
                        TRIP_DATE
                );

        assertThat(result).containsExactly(overlappingMembership);
    }

    @DisplayName("사용자가 탈퇴한 멤버십은 일정 중복 검사에서 제외한다")
    @Test
    void excludesMembershipThatUserLeft() {
        User user = createUser();
        Trip trip = createTrip();
        TripMember member = TripMember.createMember(trip, user);
        ReflectionTestUtils.setField(member, "activeSlot", (byte) 0);
        ReflectionTestUtils.setField(
                member,
                "leftAt",
                LocalDateTime.now()
        );
        tripMemberRepository.save(member);

        long count = tripMemberRepository.countActiveTripsOverlapping(
                user,
                TRIP_DATE.plusDays(1),
                TRIP_DATE.plusDays(1)
        );

        assertThat(count).isZero();
    }

    @DisplayName("삭제된 여행은 일정 중복 검사에서 제외한다")
    @Test
    void excludesDeletedTrip() {
        User user = createUser();
        Trip trip = createTrip();
        ReflectionTestUtils.setField(
                trip,
                "deletedAt",
                LocalDateTime.now()
        );
        tripRepository.save(trip);
        tripMemberRepository.save(TripMember.createMember(trip, user));

        long count = tripMemberRepository.countActiveTripsOverlapping(
                user,
                TRIP_DATE.plusDays(1),
                TRIP_DATE.plusDays(1)
        );

        assertThat(count).isZero();
    }

    @DisplayName("여행 중, 예정, 완료 순으로 여행을 조회한다")
    @Test
    void listsTripsByProgressOrder() {
        LocalDate referenceDate = LocalDate.of(2026, 9, 23);
        User user = createUser();
        Trip upcomingLater = createTrip(
                "부산 여행",
                referenceDate.plusDays(2),
                referenceDate.plusDays(4)
        );
        Trip completedRecent = createTrip(
                "전주 여행",
                referenceDate.minusDays(3),
                referenceDate.minusDays(1)
        );
        Trip upcomingSoon = createTrip(
                "경주 여행",
                referenceDate.plusDays(1),
                referenceDate.plusDays(3)
        );
        Trip inProgress = createTrip(
                "서울 여행",
                referenceDate.minusDays(1),
                referenceDate.plusDays(1)
        );
        Trip completedOlder = createTrip(
                "제주 여행",
                referenceDate.minusDays(5),
                referenceDate.minusDays(2)
        );

        tripMemberRepository.saveAll(List.of(
                TripMember.createMember(upcomingLater, user),
                TripMember.createMember(completedRecent, user),
                TripMember.createMember(upcomingSoon, user),
                TripMember.createMember(inProgress, user),
                TripMember.createMember(completedOlder, user)
        ));

        List<TripMember> memberships =
                tripMemberRepository.findActiveTripMemberships(
                        user,
                        referenceDate,
                        PageRequest.of(0, 10)
                );

        assertThat(memberships)
                .extracting(TripMember::getTrip)
                .containsExactly(
                        inProgress,
                        upcomingSoon,
                        upcomingLater,
                        completedRecent,
                        completedOlder
                );
    }

    @DisplayName("예정 여행 커서 이후의 여행을 구간에 걸쳐 조회한다")
    @Test
    void listsTripsAfterUpcomingCursorAcrossSections() {
        LocalDate referenceDate = LocalDate.of(2026, 9, 23);
        User user = createUser();
        Trip inProgress = createTrip(
                "서울 여행",
                referenceDate.minusDays(1),
                referenceDate.plusDays(1)
        );
        Trip upcomingSoon = createTrip(
                "경주 여행",
                referenceDate.plusDays(1)
        );
        Trip upcomingLater = createTrip(
                "부산 여행",
                referenceDate.plusDays(2)
        );
        Trip completedTrip = createTrip(
                "제주 여행",
                referenceDate.minusDays(3),
                referenceDate.minusDays(1)
        );

        tripMemberRepository.saveAll(List.of(
                TripMember.createMember(inProgress, user),
                TripMember.createMember(upcomingSoon, user),
                TripMember.createMember(upcomingLater, user),
                TripMember.createMember(completedTrip, user)
        ));

        List<TripMember> memberships =
                tripMemberRepository.findActiveTripMembershipsAfter(
                        user,
                        referenceDate,
                        1,
                        upcomingSoon.getStartDate(),
                        upcomingSoon.getId(),
                        PageRequest.of(0, 10)
                );

        assertThat(memberships)
                .extracting(TripMember::getTrip)
                .containsExactly(upcomingLater, completedTrip);
    }

    @DisplayName("최근 완료 여행 커서 이후의 여행을 조회한다")
    @Test
    void listsPastTripsAfterPastCursor() {
        LocalDate referenceDate = LocalDate.of(2026, 9, 23);
        User user = createUser();
        Trip olderTrip = createTrip(
                "제주 여행",
                referenceDate.minusDays(5),
                referenceDate.minusDays(3)
        );
        Trip recentTrip = createTrip(
                "서울 여행",
                referenceDate.minusDays(2),
                referenceDate.minusDays(1)
        );

        tripMemberRepository.saveAll(List.of(
                TripMember.createMember(olderTrip, user),
                TripMember.createMember(recentTrip, user)
        ));

        List<TripMember> memberships =
                tripMemberRepository.findActiveTripMembershipsAfter(
                        user,
                        referenceDate,
                        2,
                        recentTrip.getEndDate(),
                        recentTrip.getId(),
                        PageRequest.of(0, 10)
                );

        assertThat(memberships)
                .extracting(TripMember::getTrip)
                .containsExactly(olderTrip);
    }

    @DisplayName("커서로 모든 여행을 중복과 누락 없이 조회한다")
    @Test
    void pagesTripsWithoutDuplicatesOrOmissions() {
        LocalDate referenceDate = LocalDate.now();
        User user = createUser();
        Trip inProgress = createTrip(
                "서울 여행",
                referenceDate.minusDays(1),
                referenceDate.plusDays(1)
        );
        Trip upcomingFirst = createTrip(
                "부산 여행",
                referenceDate.plusDays(2),
                referenceDate.plusDays(4)
        );
        Trip upcomingSecond = createTrip(
                "경주 여행",
                referenceDate.plusDays(2),
                referenceDate.plusDays(4)
        );
        Trip completedFirst = createTrip(
                "전주 여행",
                referenceDate.minusDays(3),
                referenceDate.minusDays(1)
        );
        Trip completedSecond = createTrip(
                "대구 여행",
                referenceDate.minusDays(2),
                referenceDate.minusDays(1)
        );
        Trip completedOlder = createTrip(
                "제주 여행",
                referenceDate.minusDays(6),
                referenceDate.minusDays(4)
        );

        tripMemberRepository.saveAll(List.of(
                TripMember.createMember(inProgress, user),
                TripMember.createMember(upcomingFirst, user),
                TripMember.createMember(upcomingSecond, user),
                TripMember.createMember(completedFirst, user),
                TripMember.createMember(completedSecond, user),
                TripMember.createMember(completedOlder, user)
        ));

        List<String> tripIds = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 3; page++) {
            TripListResponse response = tripService.getTrips(
                    user.getPublicId().toString(),
                    cursor,
                    2
            );
            tripIds.addAll(response.trips().stream()
                    .map(TripListResponse.TripSummary::tripId)
                    .toList());
            cursor = response.nextCursor();
        }

        assertThat(tripIds).containsExactly(
                inProgress.getId().toString(),
                upcomingFirst.getId().toString(),
                upcomingSecond.getId().toString(),
                completedFirst.getId().toString(),
                completedSecond.getId().toString(),
                completedOlder.getId().toString()
        );
        assertThat(cursor).isNull();
    }

    @DisplayName("탈퇴한 사용자를 여행 멤버 목록에서 제외한다")
    @Test
    void excludesWithdrawnUserFromTripMembers() {
        Trip trip = createTrip();
        User activeUser = createUser();
        User withdrawnUser = createUser();
        withdrawnUser.withdraw(LocalDateTime.now());
        userRepository.save(withdrawnUser);

        TripMember activeMember = TripMember.createMember(trip, activeUser);
        tripMemberRepository.saveAll(List.of(
                activeMember,
                TripMember.createMember(trip, withdrawnUser)
        ));

        List<TripMember> members =
                tripMemberRepository.findActiveMembersByTripIds(
                        List.of(trip.getId())
                );

        assertThat(members).containsExactly(activeMember);
    }

    @DisplayName("탈퇴한 멤버십과 삭제된 여행을 목록에서 제외한다")
    @Test
    void excludesLeftMembershipAndDeletedTripFromList() {
        LocalDate referenceDate = LocalDate.of(2026, 9, 23);
        User user = createUser();
        Trip activeTrip = createTrip(
                "경주 여행",
                referenceDate.plusDays(1)
        );
        Trip leftTrip = createTrip(
                "부산 여행",
                referenceDate.plusDays(2)
        );
        Trip deletedTrip = createTrip(
                "제주 여행",
                referenceDate.plusDays(3)
        );

        TripMember leftMembership =
                TripMember.createMember(leftTrip, user);
        ReflectionTestUtils.setField(
                leftMembership,
                "activeSlot",
                (byte) 0
        );
        ReflectionTestUtils.setField(
                leftMembership,
                "leftAt",
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(
                deletedTrip,
                "deletedAt",
                LocalDateTime.now()
        );

        tripRepository.save(deletedTrip);
        tripMemberRepository.saveAll(List.of(
                TripMember.createMember(activeTrip, user),
                leftMembership,
                TripMember.createMember(deletedTrip, user)
        ));

        List<TripMember> memberships =
                tripMemberRepository.findActiveTripMemberships(
                        user,
                        referenceDate,
                        PageRequest.of(0, 10)
                );

        assertThat(memberships)
                .extracting(TripMember::getTrip)
                .containsExactly(activeTrip);
    }

    @DisplayName("활성 멤버만 참여 순서대로 조회한다")
    @Test
    void findsOnlyActiveMembersInJoinOrder() {
        Trip trip = createTrip();
        User firstUser = createUser();
        User secondUser = createUser();
        User leftUser = createUser();
        User withdrawnUser = createUser();
        withdrawnUser.withdraw(LocalDateTime.now());
        userRepository.save(withdrawnUser);

        LocalDateTime joinedAt = LocalDateTime.of(
                2026,
                9,
                24,
                12,
                0
        );
        TripMember firstMember = TripMember.createMember(trip, firstUser);
        TripMember secondMember = TripMember.createMember(trip, secondUser);
        TripMember leftMember = TripMember.createMember(trip, leftUser);
        TripMember withdrawnMember =
                TripMember.createMember(trip, withdrawnUser);

        List.of(firstMember, secondMember, leftMember, withdrawnMember)
                .forEach(member -> ReflectionTestUtils.setField(
                        member,
                        "joinedAt",
                        joinedAt
                ));
        ReflectionTestUtils.setField(leftMember, "activeSlot", (byte) 0);
        ReflectionTestUtils.setField(
                leftMember,
                "leftAt",
                joinedAt.plusHours(1)
        );
        tripMemberRepository.saveAll(List.of(
                firstMember,
                secondMember,
                leftMember,
                withdrawnMember
        ));

        List<TripMember> members =
                tripMemberRepository.findActiveMembersByTrip(trip);

        assertThat(members).containsExactly(firstMember, secondMember);
    }

    @DisplayName("한 여행에 여러 명의 활성 방장이 존재할 수 없다")
    @Test
    void preventsMultipleActiveHostsInSameTrip() {
        Trip trip = createTrip();
        tripMemberRepository.saveAndFlush(
                TripMember.createHost(trip, createUser())
        );

        assertThatThrownBy(() -> tripMemberRepository.saveAndFlush(
                TripMember.createHost(trip, createUser())
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private User createUser() {
        return userRepository.save(new User(
                null,
                UUID.randomUUID(),
                "여행테스트사용자"
        ));
    }

    private Trip createTrip() {
        return createTrip("제주 여행", TRIP_DATE);
    }

    private Trip createTrip(String name, LocalDate tripDate) {
        return createTrip(name, tripDate, tripDate.plusDays(2));
    }

    private Trip createTrip(
            String name,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Region region = regionRepository.findById(1L)
                .orElseThrow();

        return tripRepository.save(new Trip(
                region,
                name,
                startDate,
                endDate,
                (byte) 4,
                startDate.minusDays(1)
                        .atTime(23, 59, 59, 999_999_000)
        ));
    }
}
