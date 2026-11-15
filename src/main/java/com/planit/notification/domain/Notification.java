package com.planit.notification.domain;

import com.planit.domain.User;
import com.planit.mq.job.AiJobType;

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

    private static final String AI_JOB_DEDUPE_PREFIX = "AI_JOB";

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

    /**
     * AI 작업 결과를 여행 대상으로 알린다.
     * 같은 작업의 결과가 여러 번 반영돼도 한 번만 저장되도록 작업과 수신자로 중복 방지 키를 만든다.
     */
    public static Notification createAiJobTripNotification(
            User recipient,
            AiJobType jobType,
            Long jobId,
            Long tripId,
            NotificationType type,
            String title,
            String body,
            LocalDateTime createdAt
    ) {
        Objects.requireNonNull(tripId, "알림 여행 ID는 필수입니다");
        return createAiJobNotification(
                recipient,
                jobType,
                jobId,
                NotificationTargetType.TRIP,
                tripId,
                type,
                title,
                body,
                createdAt
        );
    }

    public static Notification createAiJobPhotoMissionNotification(
            User recipient,
            AiJobType jobType,
            Long jobId,
            Long missionId,
            NotificationType type,
            String title,
            String body,
            LocalDateTime createdAt
    ) {
        Objects.requireNonNull(missionId, "알림 포토미션 ID는 필수입니다");
        return createAiJobNotification(
                recipient,
                jobType,
                jobId,
                NotificationTargetType.PHOTO_MISSION,
                missionId,
                type,
                title,
                body,
                createdAt
        );
    }

    private static Notification createAiJobNotification(
            User recipient,
            AiJobType jobType,
            Long jobId,
            NotificationTargetType targetType,
            Long targetId,
            NotificationType type,
            String title,
            String body,
            LocalDateTime createdAt
    ) {
        validateAiJobNotification(
                recipient,
                jobType,
                jobId,
                targetType,
                targetId,
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
        notification.targetType = targetType;
        notification.targetId = targetId;
        notification.targetStatus = NotificationTargetStatus.ACTIVE;
        notification.dedupeKey = String.join(
                ":",
                AI_JOB_DEDUPE_PREFIX,
                jobType.name(),
                jobId.toString(),
                type.name(),
                recipient.getId().toString()
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
        validateTripTarget(recipient, tripId, type, title, body, createdAt);
        Objects.requireNonNull(tripStartDate, "알림 여행 시작일은 필수입니다");
    }

    private static void validateAiJobNotification(
            User recipient,
            AiJobType jobType,
            Long jobId,
            NotificationTargetType targetType,
            Long targetId,
            NotificationType type,
            String title,
            String body,
            LocalDateTime createdAt
    ) {
        validateTarget(recipient, targetId, type, title, body, createdAt);
        Objects.requireNonNull(recipient.getId(), "알림 수신자 ID는 필수입니다");
        Objects.requireNonNull(jobType, "AI 작업 종류는 필수입니다");
        Objects.requireNonNull(jobId, "AI 작업 ID는 필수입니다");
        Objects.requireNonNull(targetType, "알림 대상 종류는 필수입니다");
    }

    /** 여행을 대상으로 하는 알림이 공통으로 가져야 하는 값을 검증한다. */
    private static void validateTripTarget(
            User recipient,
            Long tripId,
            NotificationType type,
            String title,
            String body,
            LocalDateTime createdAt
    ) {
        Objects.requireNonNull(tripId, "알림 여행 ID는 필수입니다");
        validateTarget(recipient, tripId, type, title, body, createdAt);
    }

    private static void validateTarget(
            User recipient,
            Long targetId,
            NotificationType type,
            String title,
            String body,
            LocalDateTime createdAt
    ) {
        Objects.requireNonNull(recipient, "알림 수신자는 필수입니다");
        Objects.requireNonNull(recipient.getPublicId(), "알림 수신자 공개 ID는 필수입니다");
        Objects.requireNonNull(targetId, "알림 대상 ID는 필수입니다");
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
