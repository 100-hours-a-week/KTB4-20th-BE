package com.planit.trip.service;

import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.repository.TripMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TripMemberAccessServiceTest {

    private final TripMemberRepository repository =
            mock(TripMemberRepository.class);
    private final TripMemberAccessService service =
            new TripMemberAccessService(repository);
    private final Trip trip = mock(Trip.class);
    private final User user = mock(User.class);

    @DisplayName("활성 방장 슬롯을 가진 멤버를 현재 방장으로 반환한다")
    @Test
    void returnsCurrentHost() {
        TripMember host = TripMember.createHost(trip, user);
        when(repository.findByTripAndUserAndLeftAtIsNull(trip, user))
                .thenReturn(Optional.of(host));

        assertThat(service.findActiveHost(
                trip,
                user,
                ErrorCode.TRIP_HOST_REQUIRED
        )).isSameAs(host);
    }

    @DisplayName("HOST 역할이 남아 있어도 방장 슬롯을 반납한 멤버는 거부한다")
    @Test
    void rejectsDemotedHostRole() {
        TripMember previousHost = TripMember.createHost(trip, user);
        previousHost.demoteFromHost();
        when(repository.findByTripAndUserAndLeftAtIsNull(trip, user))
                .thenReturn(Optional.of(previousHost));

        assertThatThrownBy(() -> service.findActiveHost(
                trip,
                user,
                ErrorCode.TRIP_HOST_REQUIRED
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.TRIP_HOST_REQUIRED)
        );
    }
}
