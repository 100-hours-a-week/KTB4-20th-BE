package com.planit.photomission.service;

import com.planit.mq.config.RabbitMqConfig;
import com.planit.mq.job.AiJobType;
import com.planit.mq.outbox.MessageOutboxRepository;
import com.planit.photomission.repository.PhotoEvaluationRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class PhotoEvaluationFailureService {

    public static final String PROCESSING_TIMEOUT = "PROCESSING_TIMEOUT";

    private final PhotoEvaluationRepository evaluationRepository;
    private final MessageOutboxRepository outboxRepository;
    private final PhotoEvaluationNotificationService notificationService;
    private final Clock clock;

    public PhotoEvaluationFailureService(
            PhotoEvaluationRepository evaluationRepository,
            MessageOutboxRepository outboxRepository,
            PhotoEvaluationNotificationService notificationService,
            @Qualifier(RabbitMqConfig.MQ_CLOCK) Clock clock
    ) {
        this.evaluationRepository = evaluationRepository;
        this.outboxRepository = outboxRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public boolean failFromDlq(
            Long evaluationId,
            int executionAttemptCount,
            String errorCode
    ) {
        LocalDateTime failedAt = now();
        int updated = evaluationRepository.failQueued(
                evaluationId,
                executionAttemptCount,
                errorCode,
                failedAt
        );
        if (updated == 0) {
            return false;
        }
        notificationService.notifyFailed(
                evaluationRepository.findById(evaluationId).orElseThrow(),
                failedAt
        );
        return true;
    }

    @Transactional
    public boolean failDeadline(Long evaluationId) {
        if (!failPreservingAttempt(evaluationId, PROCESSING_TIMEOUT)) {
            return false;
        }
        outboxRepository.markFailedPendingByJob(
                AiJobType.PHOTO_JUDGE,
                evaluationId,
                PROCESSING_TIMEOUT
        );
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean failWithoutAttempt(Long evaluationId, String errorCode) {
        return failPreservingAttempt(evaluationId, errorCode);
    }

    private boolean failPreservingAttempt(Long evaluationId, String errorCode) {
        LocalDateTime failedAt = now();
        int updated = evaluationRepository.failQueuedPreservingAttempt(
                evaluationId,
                errorCode,
                failedAt
        );
        if (updated == 0) {
            return false;
        }
        notificationService.notifyFailed(
                evaluationRepository.findById(evaluationId).orElseThrow(),
                failedAt
        );
        return true;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
