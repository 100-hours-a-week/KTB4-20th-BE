package com.planit.mq.job;

import com.planit.mq.outbox.MessageOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기준 시간 안에 결과가 오지 않은 작업 하나를 한 트랜잭션에서 실패로 끝낸다.
 * 작업을 이번 호출이 끝냈을 때만 남은 발행 대기 Outbox를 실패로 바꿔 Relay가 더 발행하지 않게 한다.
 * 실패 알림은 공통 종료 서비스가 같은 트랜잭션에 저장한다.
 */
@Service
public class StaleJobFailureService {

    /** 기준 시간을 넘겨 실패 처리한 작업의 실패 원인. 작업 `error_code`와 Outbox `last_error`에 남긴다. */
    public static final String STALE = "STALE";

    private final AiJobTerminationService terminationService;
    private final MessageOutboxRepository outboxRepository;

    public StaleJobFailureService(
            AiJobTerminationService terminationService,
            MessageOutboxRepository outboxRepository
    ) {
        this.terminationService = terminationService;
        this.outboxRepository = outboxRepository;
    }

    /**
     * @return 이번 호출이 작업을 실패로 끝냈으면 true. 그 사이 결과가 와서 끝났으면 false
     */
    @Transactional
    public boolean fail(AiJobType jobType, Long jobId) {
        if (!terminationService.fail(jobType, jobId, STALE)) {
            return false;
        }
        outboxRepository.markFailedPendingByJob(jobType, jobId, STALE);
        return true;
    }
}
