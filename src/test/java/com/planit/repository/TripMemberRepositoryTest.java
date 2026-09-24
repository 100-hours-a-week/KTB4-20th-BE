package com.planit.repository;

import com.planit.domain.ImageFile;
import com.planit.domain.SubRegion;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private ImageFileRepository imageFileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubRegionRepository subRegionRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Test
    void countsActiveTripOnOverlappingDate() {
        User user = createUser();
        Trip trip = createTrip();
        tripMemberRepository.save(TripMember.createMember(trip, user));

        long count = tripMemberRepository.countActiveTripsOverlapping(
                user,
                TRIP_DATE,
                TRIP_DATE
        );

        assertThat(count).isEqualTo(1);
    }

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
                TRIP_DATE,
                TRIP_DATE
        );

        assertThat(count).isZero();
    }

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
                TRIP_DATE,
                TRIP_DATE
        );

        assertThat(count).isZero();
    }

    @Test
    void listsUpcomingTripsBeforePastTrips() {
        LocalDate referenceDate = LocalDate.of(2026, 9, 23);
        User user = createUser();
        Trip upcomingLater = createTrip(
                "부산 여행",
                referenceDate.plusDays(2)
        );
        Trip pastRecent = createTrip(
                "서울 여행",
                referenceDate.minusDays(1)
        );
        Trip upcomingSoon = createTrip(
                "경주 여행",
                referenceDate.plusDays(1)
        );
        Trip pastOlder = createTrip(
                "제주 여행",
                referenceDate.minusDays(2)
        );

        tripMemberRepository.saveAll(List.of(
                TripMember.createMember(upcomingLater, user),
                TripMember.createMember(pastRecent, user),
                TripMember.createMember(upcomingSoon, user),
                TripMember.createMember(pastOlder, user)
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
                        upcomingSoon,
                        upcomingLater,
                        pastOlder,
                        pastRecent
                );
    }

    @Test
    void listsTripsAfterUpcomingCursorAcrossSections() {
        LocalDate referenceDate = LocalDate.of(2026, 9, 23);
        User user = createUser();
        Trip upcomingSoon = createTrip(
                "경주 여행",
                referenceDate.plusDays(1)
        );
        Trip upcomingLater = createTrip(
                "부산 여행",
                referenceDate.plusDays(2)
        );
        Trip pastTrip = createTrip(
                "제주 여행",
                referenceDate.minusDays(1)
        );

        tripMemberRepository.saveAll(List.of(
                TripMember.createMember(upcomingSoon, user),
                TripMember.createMember(upcomingLater, user),
                TripMember.createMember(pastTrip, user)
        ));

        List<TripMember> memberships =
                tripMemberRepository.findActiveTripMembershipsAfter(
                        user,
                        referenceDate,
                        0,
                        upcomingSoon.getStartDate(),
                        PageRequest.of(0, 10)
                );

        assertThat(memberships)
                .extracting(TripMember::getTrip)
                .containsExactly(upcomingLater, pastTrip);
    }

    @Test
    void listsPastTripsAfterPastCursor() {
        LocalDate referenceDate = LocalDate.of(2026, 9, 23);
        User user = createUser();
        Trip olderTrip = createTrip(
                "제주 여행",
                referenceDate.minusDays(2)
        );
        Trip recentTrip = createTrip(
                "서울 여행",
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
                        1,
                        olderTrip.getStartDate(),
                        PageRequest.of(0, 10)
                );

        assertThat(memberships)
                .extracting(TripMember::getTrip)
                .containsExactly(recentTrip);
    }

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
        ImageFile imageFile = imageFileRepository.findAll().stream()
                .findFirst()
                .orElseThrow();

        return userRepository.save(new User(
                imageFile,
                UUID.randomUUID(),
                "여행테스트사용자"
        ));
    }

    private Trip createTrip() {
        return createTrip("제주 여행", TRIP_DATE);
    }

    private Trip createTrip(String name, LocalDate tripDate) {
        SubRegion subRegion = subRegionRepository.findById(1L)
                .orElseThrow();

        return tripRepository.save(new Trip(
                subRegion,
                name,
                tripDate,
                (byte) 4,
                tripDate.minusDays(1)
                        .atTime(23, 59, 59, 999_999_000)
        ));
    }
}
