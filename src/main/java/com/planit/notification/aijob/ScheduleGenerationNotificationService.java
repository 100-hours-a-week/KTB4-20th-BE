package com.planit.notification.aijob;

import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.mq.job.AiJobType;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import com.planit.repository.TripMemberRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 일정 생성 작업의 성공·실패를 여행방 활성 멤버 전원에게 알린다. */
@Service
public class ScheduleGenerationNotificationService {

    private static final String SUCCEEDED_TITLE = "일정이 완성됐어요";
    private static final String SUCCEEDED_BODY_FORMAT = "%s의 일정 3가지를 확인해 보세요.";
    private static final String FAILED_TITLE = "일정을 만들지 못했어요";
    private static final String FAILED_BODY = "잠시 후 다시 시도해 주세요.";

    private final TripMemberRepository tripMemberRepository;
    private final AiJobNotificationService notificationService;
    private final Clock clock;

    public ScheduleGenerationNotificationService(
            TripMemberRepository tripMemberRepository,
            AiJobNotificationService notificationService,
            @Qualifier("tripRemindClock") Clock clock
    ) {
        this.tripMemberRepository = tripMemberRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /** 일정 생성 작업을 성공으로 바꾼 트랜잭션 안에서 호출한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifySucceeded(Long jobId, Trip trip) {
        Objects.requireNonNull(trip, "알림 여행은 필수입니다");
        notifyMembers(
                jobId,
                trip,
                NotificationType.SCHEDULE_GENERATION_SUCCEEDED,
                SUCCEEDED_TITLE,
                SUCCEEDED_BODY_FORMAT.formatted(trip.getName())
        );
    }

    /** 일정 생성 작업을 실패로 바꾼 트랜잭션 안에서 호출한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyFailed(Long jobId, Trip trip) {
        notifyMembers(
                jobId,
                trip,
                NotificationType.SCHEDULE_GENERATION_FAILED,
                FAILED_TITLE,
                FAILED_BODY
        );
    }

    private void notifyMembers(
            Long jobId,
            Trip trip,
            NotificationType type,
            String title,
            String body
    ) {
        Objects.requireNonNull(trip, "알림 여행은 필수입니다");
        LocalDateTime createdAt = LocalDateTime.now(clock);
        List<Notification> notifications = tripMemberRepository.findActiveMembersByTrip(trip).stream()
                .map(TripMember::getUser)
                .map(recipient -> Notification.createAiJobTripNotification(
                        recipient,
                        AiJobType.SCHEDULE_GENERATE,
                        jobId,
                        trip.getId(),
                        type,
                        title,
                        body,
                        createdAt
                ))
                .toList();
        notificationService.saveAndPublishAfterCommit(notifications);
    }
}
