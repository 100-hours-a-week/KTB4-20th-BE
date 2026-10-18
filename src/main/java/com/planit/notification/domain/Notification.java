package com.planit.notification.domain;

import com.planit.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notifications_dedupe",
                columnNames = "dedupe_key"
        )
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User recipient;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "weather_condition", length = 30)
    private WeatherCondition weatherCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 50)
    private NotificationTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_status", nullable = false, length = 20)
    private NotificationTargetStatus targetStatus;

    @Column(name = "dedupe_key", nullable = false, length = 191)
    private String dedupeKey;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "target_expired_at")
    private LocalDateTime targetExpiredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Notification() {
    }

    public static Notification createTripNotification(
            User recipient,
            Long tripId,
            LocalDate tripStartDate,
            NotificationType type,
            String title,
            String body,
            LocalDateTime createdAt
    ) {
        return createTripNotification(
                recipient,
                tripId,
                tripStartDate,
                type,
                null,
                title,
                body,
                createdAt
        );
    }

    public static Notification createTripNotification(
            User recipient,
            Long tripId,
            LocalDate tripStartDate,
            NotificationType type,
            WeatherCondition weatherCondition,
            String title,
            String body,
            LocalDateTime createdAt
    ) {
        validateTripNotification(
                recipient,
                tripId,
                tripStartDate,
                type,
                title,
                body,
                createdAt
        );

        Notification notification = new Notification();
        notification.recipient = recipient;
        notification.title = title;
        notification.body = body;
        notification.type = type;
        notification.weatherCondition = weatherCondition;
        notification.targetType = NotificationTargetType.TRIP;
        notification.targetId = tripId;
        notification.targetStatus = NotificationTargetStatus.ACTIVE;
        notification.dedupeKey = createDedupeKey(
                type,
                tripId,
                recipient,
                tripStartDate
        );
        notification.createdAt = createdAt;
        return notification;
    }

    private static void validateTripNotification(
            User recipient,
            Long tripId,
            LocalDate tripStartDate,
            NotificationType type,
            String title,
            String body,
            LocalDateTime createdAt
    ) {
        Objects.requireNonNull(recipient, "알림 수신자는 필수입니다");
        Objects.requireNonNull(recipient.getPublicId(), "알림 수신자 공개 ID는 필수입니다");
        Objects.requireNonNull(tripId, "알림 여행 ID는 필수입니다");
        Objects.requireNonNull(tripStartDate, "알림 여행 시작일은 필수입니다");
        Objects.requireNonNull(type, "알림 유형은 필수입니다");
        Objects.requireNonNull(title, "알림 제목은 필수입니다");
        Objects.requireNonNull(body, "알림 본문은 필수입니다");
        Objects.requireNonNull(createdAt, "알림 생성 시각은 필수입니다");
    }

    private static String createDedupeKey(
            NotificationType type,
            Long tripId,
            User recipient,
            LocalDate tripStartDate
    ) {
        return String.join(
                ":",
                type.name(),
                tripId.toString(),
                recipient.getPublicId().toString(),
                tripStartDate.toString()
        );
    }

    public Long getId() {
        return id;
    }

    public User getRecipient() {
        return recipient;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public NotificationType getType() {
        return type;
    }

    public WeatherCondition getWeatherCondition() {
        return weatherCondition;
    }

    public NotificationTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public NotificationTargetStatus getTargetStatus() {
        return targetStatus;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public LocalDateTime getTargetExpiredAt() {
        return targetExpiredAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void markAsRead(LocalDateTime readAt) {
        Objects.requireNonNull(readAt, "알림 읽음 시각은 필수입니다");
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }
}
