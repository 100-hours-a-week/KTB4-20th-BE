package com.planit.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TripMemberTest {

    @Test
    void createsHostMembership() {
        Trip trip = createTrip();
        User user = createUser();

        TripMember member =
                TripMember.createHost(trip, user);

        assertThat(member.getTrip()).isSameAs(trip);
        assertThat(member.getUser()).isSameAs(user);
        assertThat(member.getRole())
                .isEqualTo(TripMemberRole.HOST);
        assertThat(member.getHostSlot()).isEqualTo((byte) 1);
        assertThat(member.getActiveSlot()).isEqualTo((byte) 1);
        assertThat(member.getJoinedAt()).isNotNull();
        assertThat(member.getLeftAt()).isNull();
    }

    @Test
    void createsGeneralMembership() {
        Trip trip = createTrip();
        User user = createUser();

        TripMember member =
                TripMember.createMember(trip, user);

        assertThat(member.getRole())
                .isEqualTo(TripMemberRole.MEMBER);
        assertThat(member.getHostSlot()).isNull();
        assertThat(member.getActiveSlot()).isEqualTo((byte) 1);
        assertThat(member.getLeftAt()).isNull();
    }

    private Trip createTrip() {
        return new Trip(
                new SubRegion(),
                "부산 맛집 여행",
                LocalDate.of(2026, 9, 23),
                (byte) 4,
                LocalDateTime.of(
                        2026,
                        9,
                        22,
                        23,
                        59,
                        59,
                        999_999_000
                )
        );
    }

    private User createUser() {
        return new User(
                new ImageFile(),
                UUID.fromString(
                        "01991f6e-7300-7b21-a3cc-1436db3df95e"
                ),
                "플랜잇사용자"
        );
    }
}
