package com.planit.trip.service;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 매일 서울 시각 23:50에 다음 날 시작하는 여행 중 ACTIVE 확정 일정이 없는 여행방을 삭제한다.
 * 23:50은 후보 확정·재확정 마감 시각과 같으므로 이후에는 확정 일정이 새로 생길 수 없다.
 */
@Slf4j
@Component
public class UnconfirmedTripDeletionScheduler {

    private final UnconfirmedTripDeletionService deletionService;
    private final Clock clock;

    public UnconfirmedTripDeletionScheduler(
            UnconfirmedTripDeletionService deletionService,
            @Qualifier("unconfirmedTripDeletionClock") Clock clock
    ) {
        this.deletionService = deletionService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    @SchedulerLock(
            name = "unconfirmedTripDeletion",
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT1M"
    )
    public void deleteUnconfirmedTrips() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate targetDate = now.toLocalDate().plusDays(1);
        int deletedCount = 0;

        // 여행마다 별도 트랜잭션으로 처리해 한 여행의 실패가 다른 여행 삭제를 막지 않게 한다.
        for (Long tripId : deletionService.findTargetTripIds(targetDate)) {
            try {
                if (deletionService.deleteIfUnconfirmed(
                        tripId,
                        targetDate,
                        now
                )) {
                    deletedCount++;
                }
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to delete unconfirmed trip. tripId={}",
                        tripId,
                        exception
                );
            }
        }

        log.info(
                "Deleted unconfirmed trips. startDate={}, count={}",
                targetDate,
                deletedCount
        );
    }
}
