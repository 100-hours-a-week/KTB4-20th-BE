package com.planit.notification.service;

import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.notification.repository.NotificationStore;
import com.planit.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class NotificationDeliveryService {

    private final UserRepository userRepository;
    private final NotificationStore notificationStore;
    private final Clock clock;

    public NotificationDeliveryService(
            UserRepository userRepository,
            NotificationStore notificationStore,
            @Qualifier("tripRemindClock") Clock clock
    ) {
        this.userRepository = userRepository;
        this.notificationStore = notificationStore;
        this.clock = clock;
    }

    @Transactional
    public void acknowledge(
            String userPublicId,
            Long notificationId
    ) {
        User recipient = userRepository.findByPublicIdAndDeletedAtIsNull(
                        UUID.fromString(userPublicId)
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOTIFICATION_NOT_FOUND
                ));
        notificationStore.acknowledgeDelivery(
                        notificationId,
                        recipient.getId(),
                        LocalDateTime.now(clock)
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOTIFICATION_NOT_FOUND
                ));
    }
}
