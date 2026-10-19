package com.planit.notification.precheck;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.domain.TripMemberRole;
import com.planit.domain.User;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.domain.WeatherCondition;
import com.planit.notification.repository.NotificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripRemindNotificationGenerationServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-15T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 10, 16);

    private TripRemindNotificationTargetService targetService;
    private NotificationStore notificationStore;
    private TripRemindNotificationGenerationService service;

    @BeforeEach
    void setUp() {
        targetService = mock(TripRemindNotificationTargetService.class);
        notificationStore = mock(NotificationStore.class);
        service = new TripRemindNotificationGenerationService(
                targetService,
                notificationStore,
                CLOCK
        );
    }

    @DisplayName("일반 D-1 알림은 여행 지역 이름으로 문구를 만든다")
    @Test
    void createsD1NotificationWithRegionName() {
        Trip trip = trip(1L, "경주");
        givenTargets(new TripRemindNotificationTarget(
                trip,
                user("0199f4de-5010-7b4a-91d8-36ca51a76b21"),
                TripMemberRole.MEMBER,
                NotificationType.TRIP_D1,
                null,
                "Daniel"
        ));

        Notification notification = generateSingle();

        assertThat(notification.getType()).isEqualTo(NotificationType.TRIP_D1);
        assertThat(notification.getTitle()).isEqualTo("경주 여행이 내일 시작돼요");
        assertThat(notification.getBody())
                .isEqualTo("D-1, 준비물과 일정을 미리 확인해보세요.");
        assertThat(notification.getWeatherCondition()).isNull();
        assertThat(notification.getTargetId()).isEqualTo(1L);
        assertThat(notification.getCreatedAt())
                .isEqualTo(LocalDateTime.of(2026, 10, 15, 10, 0));
    }

    @DisplayName("방장의 이상 기후 알림은 날씨 조건과 일정 변경 질문을 담는다")
    @Test
    void createsHostWeatherNotification() {
        givenTargets(new TripRemindNotificationTarget(
                trip(2L, "경주"),
                user("0199f4de-5010-7b4a-91d8-36ca51a76b22"),
                TripMemberRole.HOST,
                NotificationType.TRIP_WEATHER_ALERT,
                WeatherCondition.HEAVY_RAIN,
                "Daniel"
        ));

        Notification notification = generateSingle();

        assertThat(notification.getTitle()).isEqualTo("내일 폭우가 예상돼요");
        assertThat(notification.getBody()).isEqualTo(
                "D-1, 경주 지역에 폭우가 예보되었어요. 일정을 변경할까요?"
        );
        assertThat(notification.getWeatherCondition())
                .isEqualTo(WeatherCondition.HEAVY_RAIN);
    }

    @DisplayName("멤버의 이상 기후 알림은 방장이 결정 중임을 알린다")
    @Test
    void createsMemberWeatherNotification() {
        givenTargets(new TripRemindNotificationTarget(
                trip(3L, "부산"),
                user("0199f4de-5010-7b4a-91d8-36ca51a76b23"),
                TripMemberRole.MEMBER,
                NotificationType.TRIP_WEATHER_ALERT,
                WeatherCondition.HEAT,
                "Daniel"
        ));

        Notification notification = generateSingle();

        assertThat(notification.getTitle()).isEqualTo("내일 폭염이 예상돼요");
        assertThat(notification.getBody())
                .isEqualTo("Daniel님이 일정 변경 여부를 결정하고 있어요.");
    }

    @DisplayName("날씨 조건을 알 수 없으면 특이 기상으로 표시한다")
    @Test
    void usesGenericLabelForUnknownWeather() {
        givenTargets(new TripRemindNotificationTarget(
                trip(4L, "제주"),
                user("0199f4de-5010-7b4a-91d8-36ca51a76b24"),
                TripMemberRole.MEMBER,
                NotificationType.TRIP_WEATHER_ALERT,
                null,
                null
        ));

        Notification notification = generateSingle();

        assertThat(notification.getTitle()).isEqualTo("내일 특이 기상이 예상돼요");
        assertThat(notification.getBody())
                .isEqualTo("방장님이 일정 변경 여부를 결정하고 있어요.");
        assertThat(notification.getWeatherCondition()).isNull();
    }

    @DisplayName("받침 여부에 따라 주격 조사를 붙인다")
    @Test
    void appendsSubjectParticle() {
        assertThat(TripRemindNotificationGenerationService
                .withSubjectParticle("비")).isEqualTo("비가");
        assertThat(TripRemindNotificationGenerationService
                .withSubjectParticle("태풍")).isEqualTo("태풍이");
        assertThat(TripRemindNotificationGenerationService
                .withSubjectParticle("미세먼지")).isEqualTo("미세먼지가");
    }

    private void givenTargets(TripRemindNotificationTarget... targets) {
        when(targetService.findTargets(TARGET_DATE)).thenReturn(List.of(targets));
    }

    private Notification generateSingle() {
        service.generate(TARGET_DATE);
        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationStore, times(1)).saveIdempotently(captor.capture());
        return captor.getValue();
    }

    private Trip trip(Long id, String regionName) {
        Trip trip = mock(Trip.class);
        Region region = mock(Region.class);
        when(trip.getId()).thenReturn(id);
        when(trip.getStartDate()).thenReturn(TARGET_DATE);
        when(trip.getRegion()).thenReturn(region);
        when(region.getName()).thenReturn(regionName);
        return trip;
    }

    private User user(String publicId) {
        User user = mock(User.class);
        when(user.getPublicId()).thenReturn(UUID.fromString(publicId));
        return user;
    }
}
