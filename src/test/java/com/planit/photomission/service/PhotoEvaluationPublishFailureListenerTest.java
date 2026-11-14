package com.planit.photomission.service;

import com.planit.mq.job.AiJobType;
import com.planit.mq.outbox.PublishFailureCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PhotoEvaluationPublishFailureListenerTest {

    private final PhotoEvaluationFailureService failureService = mock(PhotoEvaluationFailureService.class);
    private final PhotoEvaluationPublishFailureListener listener =
            new PhotoEvaluationPublishFailureListener(failureService);

    @DisplayName("PHOTO_JUDGE 발행을 포기하면 실행 차수를 바꾸지 않고 작업을 실패 처리한다")
    @Test
    void failsPhotoEvaluationPublish() {
        listener.onPublishFailed(AiJobType.PHOTO_JUDGE, 51L, PublishFailureCode.PUBLISH_FAILED);

        verify(failureService).failWithoutAttempt(51L, "PUBLISH_FAILED");
    }

    @DisplayName("다른 작업 종류는 공통 발행 실패 Listener에 맡긴다")
    @Test
    void ignoresOtherJobTypes() {
        listener.onPublishFailed(AiJobType.SCHEDULE_GENERATE, 51L, PublishFailureCode.PUBLISH_FAILED);

        verify(failureService, never()).failWithoutAttempt(51L, "PUBLISH_FAILED");
    }
}
