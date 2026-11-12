package com.planit.photomission.service;

import com.planit.domain.Trip;
import com.planit.domain.User;
import com.planit.mq.job.AiJobType;
import com.planit.notification.aijob.AiJobNotificationService;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.domain.PhotoEvaluationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PhotoEvaluationNotificationService {

    private static final String SUCCEEDED_TITLE = "포토미션을 완료했어요";
    private static final String SUCCEEDED_BODY = "판정 결과를 확인해 보세요.";
    private static final String FAILED_TITLE = "포토미션 판정을 완료하지 못했어요";
    private static final String FAILED_BODY = "사진을 확인하고 다시 시도해 주세요.";

    private final AiJobNotificationService notificationService;

    public PhotoEvaluationNotificationService(
            AiJobNotificationService notificationService
    ) {
        this.notificationService = notificationService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyCompleted(
            PhotoEvaluation evaluation,
            LocalDateTime completedAt
    ) {
        boolean succeeded = evaluation.getResult()
                == PhotoEvaluationResult.SUCCESS;
        notify(
                evaluation,
                succeeded
                        ? NotificationType.PHOTO_MISSION_EVALUATION_SUCCEEDED
                        : NotificationType.PHOTO_MISSION_EVALUATION_FAILED,
                succeeded ? SUCCEEDED_TITLE : FAILED_TITLE,
                succeeded ? SUCCEEDED_BODY : FAILED_BODY,
                completedAt
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyFailed(
            PhotoEvaluation evaluation,
            LocalDateTime failedAt
    ) {
        notify(
                evaluation,
                NotificationType.PHOTO_MISSION_EVALUATION_FAILED,
                FAILED_TITLE,
                FAILED_BODY,
                failedAt
        );
    }

    private void notify(
            PhotoEvaluation evaluation,
            NotificationType type,
            String title,
            String body,
            LocalDateTime createdAt
    ) {
        User recipient = evaluation.getMissionPhoto()
                .getSubmittedBy()
                .getUser();
        Trip trip = evaluation.getMissionPhoto()
                .getMission()
                .getTrip();
        Notification notification = Notification.createAiJobTripNotification(
                recipient,
                AiJobType.PHOTO_JUDGE,
                evaluation.getId(),
                trip.getId(),
                type,
                title,
                body,
                createdAt
        );
        notificationService.saveAndPublishAfterCommit(List.of(notification));
    }
}
