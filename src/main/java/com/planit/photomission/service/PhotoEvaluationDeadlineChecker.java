package com.planit.photomission.service;

import com.planit.mq.config.RabbitMqConfig;
import com.planit.photomission.repository.PhotoEvaluationRepository;
import io.sentry.Sentry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class PhotoEvaluationDeadlineChecker {

    private static final Logger log = LoggerFactory.getLogger(PhotoEvaluationDeadlineChecker.class);

    private final PhotoEvaluationRepository repository;
    private final PhotoEvaluationFailureService failureService;
    private final Clock clock;

    public PhotoEvaluationDeadlineChecker(
            PhotoEvaluationRepository repository,
            PhotoEvaluationFailureService failureService,
            @Qualifier(RabbitMqConfig.MQ_CLOCK) Clock clock
    ) {
        this.repository = repository;
        this.failureService = failureService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 */3 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "photoEvaluationDeadlineChecker", lockAtMostFor = "PT179S")
    public void check() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Long evaluationId : repository.findQueuedIdsDeadlineAtOrBefore(now)) {
            try {
                failureService.failDeadline(evaluationId);
            } catch (RuntimeException exception) {
                log.error("포토미션 판정 deadline 실패 처리 중 오류 evaluationId={}", evaluationId, exception);
                Sentry.captureException(exception);
            }
        }
    }
}
