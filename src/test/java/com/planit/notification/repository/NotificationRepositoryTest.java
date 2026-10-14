package com.planit.notification.repository;

import com.planit.domain.User;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import com.planit.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @DisplayName("중복 방지 키는 평문으로 저장되고 같은 키의 알림은 중복 저장되지 않는다")
    @Test
    void persistsPlaintextDedupeKeyAndRejectsDuplicate() {
        UUID recipientPublicId = UUID.randomUUID();
        User recipient = userRepository.save(new User(
                null,
                recipientPublicId,
                "알림저장테스트"
        ));
        LocalDate tripStartDate = LocalDate.of(2026, 10, 15);
        LocalDateTime createdAt =
                LocalDateTime.of(2026, 10, 14, 10, 0);

        Notification saved = notificationRepository.saveAndFlush(notification(
                recipient,
                tripStartDate,
                createdAt
        ));

        assertThat(saved.getDedupeKey()).isEqualTo(
                "TRIP_D1:1001:" + recipientPublicId + ":2026-10-15"
        );
        assertThatThrownBy(() -> notificationRepository.saveAndFlush(
                notification(recipient, tripStartDate, createdAt.plusMinutes(30))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Notification notification(
            User recipient,
            LocalDate tripStartDate,
            LocalDateTime createdAt
    ) {
        return Notification.createTripNotification(
                recipient,
                1001L,
                tripStartDate,
                NotificationType.TRIP_D1,
                "내일 여행이 시작돼요",
                "여행 일정과 날씨를 확인해 주세요.",
                createdAt
        );
    }
}
