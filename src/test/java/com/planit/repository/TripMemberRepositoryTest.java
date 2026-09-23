package com.planit.repository;

import com.planit.domain.ImageFile;
import com.planit.domain.SubRegion;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
        SubRegion subRegion = subRegionRepository.findById(1L)
                .orElseThrow();

        return tripRepository.save(new Trip(
                subRegion,
                "제주 여행",
                TRIP_DATE,
                (byte) 4,
                TRIP_DATE.minusDays(1)
                        .atTime(23, 59, 59, 999_999_000)
        ));
    }
}
