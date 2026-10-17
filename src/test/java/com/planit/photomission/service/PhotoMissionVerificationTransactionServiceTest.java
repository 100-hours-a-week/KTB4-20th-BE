package com.planit.photomission.service;

import com.planit.domain.ImageFile;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.image.storage.ImageStorage;
import com.planit.photomission.ai.PhotoMissionEvaluationMapper;
import com.planit.photomission.ai.PhotoMissionVerifyRequestFactory;
import com.planit.photomission.ai.PhotoMissionVerifyResponse;
import com.planit.photomission.domain.Mission;
import com.planit.photomission.domain.MissionCompletionMethod;
import com.planit.photomission.domain.MissionCompletionStatus;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionParticipation;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.domain.MissionScope;
import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.domain.PhotoEvaluationExecutionStatus;
import com.planit.photomission.domain.PhotoEvaluationResult;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionPhotoRepository;
import com.planit.photomission.repository.PhotoEvaluationRepository;
import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleVisit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhotoMissionVerificationTransactionServiceTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 10, 11, 10, 0);

    private MissionPhotoRepository photoRepository;
    private PhotoEvaluationRepository evaluationRepository;
    private MissionGroupStateRepository groupStateRepository;
    private ImageStorage imageStorage;
    private PhotoMissionVerificationTransactionService service;

    @BeforeEach
    void setUp() {
        photoRepository = mock(MissionPhotoRepository.class);
        evaluationRepository = mock(PhotoEvaluationRepository.class);
        groupStateRepository = mock(MissionGroupStateRepository.class);
        imageStorage = mock(ImageStorage.class);
        service = new PhotoMissionVerificationTransactionService(
                photoRepository,
                evaluationRepository,
                groupStateRepository,
                imageStorage,
                new PhotoMissionVerifyRequestFactory(),
                new PhotoMissionEvaluationMapper(JsonMapper.builder().build())
        );
    }

    @DisplayName("사진 판정 실행 상태를 저장하고 AI 요청을 준비한다")
    @Test
    void preparesVerification() {
        Mission mission = mission(MissionScope.GROUP);
        MissionGroupState state = MissionGroupState.create(mission, NOW);
        MissionPhoto photo = groupPhoto(mission);
        when(photoRepository.findById(41L)).thenReturn(Optional.of(photo));
        when(groupStateRepository.findByMissionId(31L))
                .thenReturn(Optional.of(state));
        when(evaluationRepository
                .findTopByMissionPhotoIdOrderByAttemptNoDesc(41L))
                .thenReturn(Optional.empty());
        when(evaluationRepository.save(any(PhotoEvaluation.class)))
                .thenAnswer(invocation -> {
                    PhotoEvaluation evaluation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(evaluation, "id", 51L);
                    return evaluation;
                });
        when(imageStorage.createReadUrl("missions/31/photo.jpg"))
                .thenReturn("https://bucket.example/photo.jpg");

        PhotoMissionVerificationTransactionService.PreparedVerification result =
                service.prepare(41L);

        assertThat(result.evaluationId()).isEqualTo(51L);
        assertThat(result.missionId()).isEqualTo(31L);
        assertThat(result.request().imageUrl())
                .isEqualTo("https://bucket.example/photo.jpg");
        assertThat(result.request().placeId()).isEqualTo("place_abc");
        assertThat(result.request().photoCoordinates()).isNotNull();
    }

    @DisplayName("단체 미션 성공 판정을 저장하고 대표 사진으로 완료한다")
    @Test
    void completesGroupMissionByAi() {
        Mission mission = mission(MissionScope.GROUP);
        MissionGroupState state = MissionGroupState.create(mission, NOW);
        MissionPhoto photo = groupPhoto(mission);
        PhotoEvaluation evaluation = evaluation(photo);
        when(evaluationRepository.findById(51L))
                .thenReturn(Optional.of(evaluation));
        when(groupStateRepository.findByMissionId(31L))
                .thenReturn(Optional.of(state));

        service.complete(51L, successResponse());

        assertThat(evaluation.getExecutionStatus())
                .isEqualTo(PhotoEvaluationExecutionStatus.SUCCEEDED);
        assertThat(evaluation.getResult())
                .isEqualTo(PhotoEvaluationResult.SUCCESS);
        assertThat(state.getStatus())
                .isEqualTo(MissionCompletionStatus.COMPLETED);
        assertThat(state.getCompletionMethod())
                .isEqualTo(MissionCompletionMethod.AI);
        assertThat(photo.getRepresentativeSlot()).isEqualTo((byte) 1);
    }

    @DisplayName("개인 미션 재시도 판정을 저장하고 실패 횟수를 증가시킨다")
    @Test
    void recordsPersonalMissionRetry() {
        Mission mission = mission(MissionScope.PERSONAL);
        MissionParticipation participation = MissionParticipation.create(
                mission,
                mock(TripMember.class),
                NOW
        );
        MissionPhoto photo = personalPhoto(participation);
        PhotoEvaluation evaluation = evaluation(photo);
        when(evaluationRepository.findById(51L))
                .thenReturn(Optional.of(evaluation));

        service.complete(51L, retryResponse());

        assertThat(evaluation.getResult())
                .isEqualTo(PhotoEvaluationResult.RETRY);
        assertThat(participation.getRetryCount()).isEqualTo(1);
        assertThat(participation.getStatus())
                .isEqualTo(MissionCompletionStatus.PENDING);
    }

    @DisplayName("완료한 개인 미션은 새 사진이 성공한 경우에만 기존 사진을 교체한다")
    @Test
    void replacesCompletedPersonalMissionPhotoAfterSuccess() {
        Mission mission = mission(MissionScope.PERSONAL);
        MissionParticipation participation = MissionParticipation.create(
                mission,
                mock(TripMember.class),
                NOW
        );
        ReflectionTestUtils.setField(participation, "id", 61L);
        participation.completeByAi(NOW);
        MissionPhoto previous = personalPhoto(participation);
        MissionPhoto candidate = MissionPhoto.personalCandidate(
                participation,
                missionImage(),
                UUID.randomUUID(),
                null,
                null,
                NOW.plusMinutes(1)
        );
        ReflectionTestUtils.setField(candidate, "id", 42L);
        PhotoEvaluation evaluation = evaluation(candidate);
        when(evaluationRepository.findById(51L))
                .thenReturn(Optional.of(evaluation));
        when(photoRepository.findActiveByParticipationId(61L))
                .thenReturn(Optional.of(previous));

        service.complete(51L, successResponse());

        assertThat(previous.getReplacedAt()).isNotNull();
        assertThat(candidate.getActiveSlot()).isEqualTo((byte) 1);
        assertThat(participation.getStatus())
                .isEqualTo(MissionCompletionStatus.COMPLETED);
    }

    @DisplayName("완료한 개인 미션의 새 사진이 실패하면 기존 완료 사진을 유지한다")
    @Test
    void keepsCompletedPersonalMissionPhotoAfterRetry() {
        Mission mission = mission(MissionScope.PERSONAL);
        MissionParticipation participation = MissionParticipation.create(
                mission,
                mock(TripMember.class),
                NOW
        );
        participation.completeByAi(NOW);
        MissionPhoto candidate = MissionPhoto.personalCandidate(
                participation,
                missionImage(),
                UUID.randomUUID(),
                null,
                null,
                NOW.plusMinutes(1)
        );
        PhotoEvaluation evaluation = evaluation(candidate);
        when(evaluationRepository.findById(51L))
                .thenReturn(Optional.of(evaluation));

        service.complete(51L, retryResponse());

        assertThat(candidate.getReplacedAt()).isNotNull();
        assertThat(participation.getStatus())
                .isEqualTo(MissionCompletionStatus.COMPLETED);
        assertThat(participation.getRetryCount()).isZero();
    }

    @DisplayName("AI 기술 오류는 실행 실패만 기록하고 미션 실패 횟수에 포함하지 않는다")
    @Test
    void recordsExecutionFailureOnly() {
        Mission mission = mission(MissionScope.PERSONAL);
        MissionParticipation participation = MissionParticipation.create(
                mission,
                mock(TripMember.class),
                NOW
        );
        PhotoEvaluation evaluation = evaluation(personalPhoto(participation));
        when(evaluationRepository.findById(51L))
                .thenReturn(Optional.of(evaluation));

        service.fail(51L, "AI_REQUEST_FAILED");

        assertThat(evaluation.getExecutionStatus())
                .isEqualTo(PhotoEvaluationExecutionStatus.FAILED);
        assertThat(evaluation.getErrorCode())
                .isEqualTo("AI_REQUEST_FAILED");
        assertThat(participation.getRetryCount()).isZero();
    }

    @DisplayName("재시도 가능한 AI 기술 오류는 대기 상태만 기록하고 미션 실패 횟수에 포함하지 않는다")
    @Test
    void recordsRetryPendingOnly() {
        Mission mission = mission(MissionScope.PERSONAL);
        MissionParticipation participation = MissionParticipation.create(
                mission,
                mock(TripMember.class),
                NOW
        );
        PhotoEvaluation evaluation = evaluation(personalPhoto(participation));
        LocalDateTime retryAfterAt = NOW.plusSeconds(30);
        when(evaluationRepository.findById(51L))
                .thenReturn(Optional.of(evaluation));

        service.waitForRetry(51L, "HTTP_429", retryAfterAt);

        assertThat(evaluation.getExecutionStatus())
                .isEqualTo(PhotoEvaluationExecutionStatus.RETRY_PENDING);
        assertThat(evaluation.getErrorCode()).isEqualTo("HTTP_429");
        assertThat(evaluation.getRetryAfterAt()).isEqualTo(retryAfterAt);
        assertThat(participation.getRetryCount()).isZero();
    }

    @DisplayName("대기 중인 기술 오류를 종료하고 다음 판정 시도를 준비한다")
    @Test
    void preparesPendingEvaluationRetry() {
        Mission mission = mission(MissionScope.GROUP);
        MissionGroupState state = MissionGroupState.create(mission, NOW);
        MissionPhoto photo = groupPhoto(mission);
        PhotoEvaluation pending = PhotoEvaluation.start(photo, 1, NOW);
        ReflectionTestUtils.setField(pending, "id", 51L);
        pending.waitForRetry("HTTP_502", NOW.plusSeconds(30));
        when(evaluationRepository.findById(51L))
                .thenReturn(Optional.of(pending));
        when(photoRepository.findById(41L)).thenReturn(Optional.of(photo));
        when(groupStateRepository.findByMissionId(31L))
                .thenReturn(Optional.of(state));
        when(evaluationRepository.save(any(PhotoEvaluation.class)))
                .thenAnswer(invocation -> {
                    PhotoEvaluation evaluation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(evaluation, "id", 52L);
                    return evaluation;
                });
        when(imageStorage.createReadUrl("missions/31/photo.jpg"))
                .thenReturn("https://bucket.example/photo.jpg");

        PhotoMissionVerificationTransactionService.PreparedVerification result =
                service.prepareRetry(51L, NOW.plusSeconds(31));

        assertThat(pending.getExecutionStatus())
                .isEqualTo(PhotoEvaluationExecutionStatus.FAILED);
        assertThat(result.evaluationId()).isEqualTo(52L);
        assertThat(result.request().imageUrl())
                .isEqualTo("https://bucket.example/photo.jpg");
    }

    @DisplayName("세 번째 기술 오류는 추가 대기 없이 실행 실패로 종료한다")
    @Test
    void stopsAfterThirdTechnicalAttempt() {
        Mission mission = mission(MissionScope.PERSONAL);
        MissionParticipation participation = MissionParticipation.create(
                mission,
                mock(TripMember.class),
                NOW
        );
        PhotoEvaluation third = PhotoEvaluation.start(
                personalPhoto(participation),
                3,
                NOW
        );
        ReflectionTestUtils.setField(third, "id", 53L);
        when(evaluationRepository.findById(53L))
                .thenReturn(Optional.of(third));

        service.waitForRetry(53L, "HTTP_504", NOW.plusSeconds(30));

        assertThat(third.getExecutionStatus())
                .isEqualTo(PhotoEvaluationExecutionStatus.FAILED);
        assertThat(third.getRetryAfterAt()).isNull();
        assertThat(participation.getRetryCount()).isZero();
    }

    private Mission mission(MissionScope scope) {
        Trip trip = mock(Trip.class);
        when(trip.getEndDate()).thenReturn(LocalDate.of(2026, 10, 12));
        Schedule schedule = mock(Schedule.class);
        ScheduleDay day = mock(ScheduleDay.class);
        when(day.getSchedule()).thenReturn(schedule);
        when(schedule.getTrip()).thenReturn(trip);

        Place place = mock(Place.class);
        when(place.getGooglePlaceId()).thenReturn("place_abc");
        when(place.getLatitude()).thenReturn(new BigDecimal("35.8347000"));
        when(place.getLongitude()).thenReturn(new BigDecimal("129.2190000"));

        ScheduleVisit visit = mock(ScheduleVisit.class);
        when(visit.getDay()).thenReturn(day);
        when(visit.getPlace()).thenReturn(place);
        when(visit.getPlaceNameSnapshot()).thenReturn("첨성대");

        Mission mission = Mission.create(
                visit,
                1,
                scope,
                "첨성대 돌 가까이 찍기",
                "HISTORY_CULTURE",
                NOW
        );
        ReflectionTestUtils.setField(mission, "id", 31L);
        return mission;
    }

    private MissionPhoto groupPhoto(Mission mission) {
        MissionPhoto photo = MissionPhoto.group(
                mission,
                mock(TripMember.class),
                missionImage(),
                UUID.randomUUID(),
                new BigDecimal("35.8350000"),
                new BigDecimal("129.2188000"),
                NOW
        );
        ReflectionTestUtils.setField(photo, "id", 41L);
        return photo;
    }

    private MissionPhoto personalPhoto(
            MissionParticipation participation
    ) {
        MissionPhoto photo = MissionPhoto.personal(
                participation,
                missionImage(),
                UUID.randomUUID(),
                null,
                null,
                NOW
        );
        ReflectionTestUtils.setField(photo, "id", 41L);
        return photo;
    }

    private ImageFile missionImage() {
        ImageFile imageFile = mock(ImageFile.class);
        when(imageFile.getImageKey())
                .thenReturn("missions/31/photo.jpg");
        return imageFile;
    }

    private PhotoEvaluation evaluation(MissionPhoto photo) {
        PhotoEvaluation evaluation = PhotoEvaluation.start(photo, 1, NOW);
        ReflectionTestUtils.setField(evaluation, "id", 51L);
        return evaluation;
    }

    private PhotoMissionVerifyResponse successResponse() {
        return new PhotoMissionVerifyResponse(
                "success",
                null,
                BigDecimal.valueOf(95),
                List.of(new PhotoMissionVerifyResponse.DetectedLabel(
                        "석탑",
                        true
                )),
                BigDecimal.valueOf(95),
                null
        );
    }

    private PhotoMissionVerifyResponse retryResponse() {
        return new PhotoMissionVerifyResponse(
                "retry",
                null,
                BigDecimal.valueOf(75),
                List.of(new PhotoMissionVerifyResponse.DetectedLabel(
                        "사람",
                        false
                )),
                BigDecimal.valueOf(92),
                "탑이 사람에 가려졌어요."
        );
    }
}
