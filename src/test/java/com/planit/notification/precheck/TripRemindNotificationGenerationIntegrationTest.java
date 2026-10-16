package com.planit.notification.precheck;

import com.planit.domain.Trip;
import com.planit.domain.User;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.repository.NotificationRepository;
import com.planit.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class TripRemindNotificationGenerationIntegrationTest {

    @Autowired
    private TripRemindNotificationGenerationService generationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TripRemindNotificationTargetService targetService;

    @DisplayName("같은 알림 생성 작업을 반복해도 대상별 알림은 하나씩만 저장된다")
    @Test
    void idempotentlyGeneratesOneNotificationPerTarget() {
        LocalDate targetDate = LocalDate.of(2026, 10, 16);
        User d1Recipient = userRepository.save(new User(
                null,
                UUID.randomUUID(),
                "D1알림수신자"
        ));
        User weatherRecipient = userRepository.save(new User(
                null,
                UUID.randomUUID(),
                "날씨알림수신자"
        ));
        Trip d1Trip = trip(10_001L, targetDate);
        Trip weatherTrip = trip(10_002L, targetDate);
        when(targetService.findTargets(targetDate)).thenReturn(List.of(
                new TripRemindNotificationTarget(
                        d1Trip,
                        d1Recipient,
                        NotificationType.TRIP_D1
                ),
                new TripRemindNotificationTarget(
                        weatherTrip,
                        weatherRecipient,
                        NotificationType.TRIP_WEATHER_ALERT
                )
        ));

        generationService.generate(targetDate);
        generationService.generate(targetDate);

        String d1Key = dedupeKey(
                NotificationType.TRIP_D1,
                d1Trip,
                d1Recipient,
                targetDate
        );
        String weatherKey = dedupeKey(
                NotificationType.TRIP_WEATHER_ALERT,
                weatherTrip,
                weatherRecipient,
                targetDate
        );
        Notification d1 = notificationRepository.findByDedupeKey(d1Key)
                .orElseThrow();
        Notification weather = notificationRepository
                .findByDedupeKey(weatherKey)
                .orElseThrow();

        assertThat(d1.getTitle()).isEqualTo("내일 여행이 시작돼요");
        assertThat(weather.getTitle())
                .isEqualTo("여행지에 특이 기상이 예상돼요");
        assertThat(countByDedupeKeys(d1Key, weatherKey)).isEqualTo(2);
    }

    private Trip trip(Long id, LocalDate startDate) {
        Trip trip = mock(Trip.class);
        when(trip.getId()).thenReturn(id);
        when(trip.getStartDate()).thenReturn(startDate);
        return trip;
    }

    private String dedupeKey(
            NotificationType type,
            Trip trip,
            User recipient,
            LocalDate startDate
    ) {
        return String.join(
                ":",
                type.name(),
                trip.getId().toString(),
                recipient.getPublicId().toString(),
                startDate.toString()
        );
    }

    private Integer countByDedupeKeys(String first, String second) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE dedupe_key IN (?, ?)",
                Integer.class,
                first,
                second
        );
    }
}
