package com.planit.mq.job;

import com.planit.mq.outbox.PublishFailureCode;
import org.springframework.stereotype.Component;

/**
 * 일정 생성, 포토미션 생성 메시지의 발행을 포기하면 작업을 실패로 끝내고 일정 생성이면 실패 알림을 저장한다.
 * Outbox를 FAILED로 바꾼 트랜잭션 안에서 실행되므로 Outbox, 작업, 알림이 함께 커밋되거나 함께 롤백된다.
 * 포토미션 판정은 판정 처리 쪽 리스너가 맡는다.
 */
@Component
public class GenerationJobPublishFailureListener implements AiJobPublishFailureListener {

    private final AiJobTerminationService terminationService;

    public GenerationJobPublishFailureListener(AiJobTerminationService terminationService) {
        this.terminationService = terminationService;
    }

    @Override
    public void onPublishFailed(AiJobType jobType, Long jobId, PublishFailureCode failureCode) {
        if (jobType == AiJobType.PHOTO_JUDGE) {
            return;
        }
        terminationService.fail(jobType, jobId, failureCode.name());
    }
}
