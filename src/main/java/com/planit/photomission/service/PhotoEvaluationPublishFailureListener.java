package com.planit.photomission.service;

import com.planit.mq.job.AiJobPublishFailureListener;
import com.planit.mq.job.AiJobType;
import com.planit.mq.outbox.PublishFailureCode;
import org.springframework.stereotype.Component;

@Component
public class PhotoEvaluationPublishFailureListener implements AiJobPublishFailureListener {

    private final PhotoEvaluationFailureService failureService;

    public PhotoEvaluationPublishFailureListener(PhotoEvaluationFailureService failureService) {
        this.failureService = failureService;
    }

    @Override
    public void onPublishFailed(AiJobType jobType, Long jobId, PublishFailureCode failureCode) {
        if (jobType != AiJobType.PHOTO_JUDGE) {
            return;
        }
        failureService.failWithoutAttempt(jobId, failureCode.name());
    }
}
