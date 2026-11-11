package com.planit.photomission.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.planit.mq.job.AiJobType;
import com.planit.photomission.ai.PhotoMissionVerifyRequest;

import java.time.OffsetDateTime;

public record PhotoEvaluationRequestedMessage(
        int schemaVersion,
        Long jobId,
        AiJobType jobType,
        OffsetDateTime deadlineAt,
        PhotoJudgeRequest request
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static PhotoEvaluationRequestedMessage create(
            Long jobId,
            OffsetDateTime deadlineAt,
            Long missionId,
            PhotoMissionVerifyRequest request
    ) {
        return new PhotoEvaluationRequestedMessage(
                CURRENT_SCHEMA_VERSION,
                jobId,
                AiJobType.PHOTO_JUDGE,
                deadlineAt,
                PhotoJudgeRequest.from(missionId, request)
        );
    }

    public record PhotoJudgeRequest(
            @JsonProperty("mission_id")
            Long missionId,
            @JsonProperty("image_url")
            String imageUrl,
            @JsonProperty("place_id")
            String placeId,
            @JsonProperty("place_name")
            String placeName,
            @JsonProperty("place_coordinates")
            PhotoMissionVerifyRequest.Coordinates placeCoordinates,
            @JsonProperty("mission_description")
            String missionDescription,
            @JsonProperty("photo_coordinates")
            PhotoMissionVerifyRequest.Coordinates photoCoordinates
    ) {
        private static PhotoJudgeRequest from(
                Long missionId,
                PhotoMissionVerifyRequest request
        ) {
            return new PhotoJudgeRequest(
                    missionId,
                    request.imageUrl(),
                    request.placeId(),
                    request.placeName(),
                    request.placeCoordinates(),
                    request.missionDescription(),
                    request.photoCoordinates()
            );
        }
    }
}
