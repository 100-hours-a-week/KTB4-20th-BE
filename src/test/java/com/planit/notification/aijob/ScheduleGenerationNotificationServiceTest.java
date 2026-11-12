package com.planit.notification.aijob;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.notification.delivery.NotificationEventPublisher;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationTargetType;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.repository.NotificationRepository;
import com.planit.repository.RegionRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 커밋 후 발행은 {@link AiJobNotificationServiceTest}에서 확인하고, 여기서는 저장 결과를 확인한다. */
@SpringBootTest
@Transactional
class ScheduleGenerationNotificationServiceTest {

    private static final Long JOB_ID = 42L;

    @Autowired
    private ScheduleGenerationNotificationService service;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @MockitoBean
    private NotificationEventPublisher eventPublisher;

    private Trip trip;
    private User host;
    private User member;

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
        host = userRepository.save(new User(null, UUID.randomUUID(), "일정알림방장"));
        member = userRepository.save(new User(null, UUID.randomUUID(), "일정알림멤버"));
        User left = userRepository.save(new User(null, UUID.randomUUID(), "일정알림나간멤버"));
        tripMemberRepository.save(TripMember.createHost(trip, host));
        tripMemberRepository.save(TripMember.createMember(trip, member));
        tripMemberRepository.save(TripMember.createMember(trip, left))
                .leave(LocalDateTime.of(2026, 11, 9, 10, 0));
    }

    @DisplayName("일정 생성에 성공하면 여행방 활성 멤버 전원에게 여행으로 이동하는 성공 알림을 저장한다")
    @Test
    void notifiesActiveMembersOnSuccess() {
        service.notifySucceeded(JOB_ID, trip);

        List<Notification> saved = savedNotifications();
        assertThat(saved)
                .extracting(notification -> notification.getRecipient().getId())
                .containsExactlyInAnyOrder(host.getId(), member.getId());
        assertThat(saved).allSatisfy(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.SCHEDULE_GENERATION_SUCCEEDED);
            assertThat(notification.getTitle()).isEqualTo("일정이 완성됐어요");
            assertThat(notification.getBody()).isEqualTo("경주 여행의 일정 3가지를 확인해 보세요.");
            assertThat(notification.getTargetType()).isEqualTo(NotificationTargetType.TRIP);
            assertThat(notification.getTargetId()).isEqualTo(trip.getId());
            assertThat(notification.getDeliveredAt()).isNull();
        });
        verify(eventPublisher, never()).publish(any());
    }

    @DisplayName("일정 생성에 실패하면 실패 알림을 저장한다")
    @Test
    void notifiesActiveMembersOnFailure() {
        service.notifyFailed(JOB_ID, trip);

        assertThat(savedNotifications()).hasSize(2).allSatisfy(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.SCHEDULE_GENERATION_FAILED);
            assertThat(notification.getTitle()).isEqualTo("일정을 만들지 못했어요");
            assertThat(notification.getBody()).isEqualTo("잠시 후 다시 시도해 주세요.");
        });
    }

    @DisplayName("같은 작업의 결과를 다시 알려도 수신자마다 한 번만 저장한다")
    @Test
    void savesOncePerJobAndRecipient() {
        service.notifySucceeded(JOB_ID, trip);
        service.notifySucceeded(JOB_ID, trip);

        assertThat(savedNotifications()).hasSize(2);
    }

    private List<Notification> savedNotifications() {
        return notificationRepository.findAll().stream()
                .filter(notification -> notification.getDedupeKey().startsWith("AI_JOB:SCHEDULE_GENERATE:" + JOB_ID + ":"))
                .toList();
    }
}
