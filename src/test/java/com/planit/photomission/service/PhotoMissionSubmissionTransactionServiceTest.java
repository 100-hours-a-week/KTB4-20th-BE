package com.planit.photomission.service;

import com.planit.domain.ImageFile;
import com.planit.domain.ImagePurpose;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.photomission.domain.Mission;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionCompletionStatus;
import com.planit.photomission.domain.MissionParticipation;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.domain.MissionScope;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionParticipationRepository;
import com.planit.photomission.repository.MissionPhotoRepository;
import com.planit.photomission.repository.MissionRepository;
import com.planit.repository.ImageFileRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleVisit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoMissionSubmissionTransactionServiceTest {

    private static final UUID USER_PUBLIC_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime SEOUL_NOW =
            LocalDateTime.of(2026, 10, 18, 10, 0);
    private static final Clock SEOUL_CLOCK = Clock.fixed(
            Instant.parse("2026-10-18T01:00:00Z"),
            SEOUL_ZONE
    );

    private MissionRepository missionRepository;
    private MissionParticipationRepository participationRepository;
    private MissionGroupStateRepository groupStateRepository;
    private MissionPhotoRepository photoRepository;
    private UserRepository userRepository;
    private TripMemberRepository tripMemberRepository;
    private ImageFileRepository imageFileRepository;
    private PhotoMissionSubmissionTransactionService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        missionRepository = mock(MissionRepository.class);
        participationRepository = mock(MissionParticipationRepository.class);
        groupStateRepository = mock(MissionGroupStateRepository.class);
        photoRepository = mock(MissionPhotoRepository.class);
        userRepository = mock(UserRepository.class);
        tripMemberRepository = mock(TripMemberRepository.class);
        imageFileRepository = mock(ImageFileRepository.class);
        clock = SEOUL_CLOCK;
        service = new PhotoMissionSubmissionTransactionService(
                missionRepository,
                participationRepository,
                groupStateRepository,
                photoRepository,
                userRepository,
                tripMemberRepository,
                imageFileRepository,
                clock
        );
    }

    @DisplayName("단체 미션 사진을 제출자와 연결해 저장한다")
    @Test
    void savesGroupMissionPhoto() {
        Fixture fixture = fixture(MissionScope.GROUP);
        MissionGroupState state = MissionGroupState.create(
                fixture.mission(), LocalDateTime.now()
        );
        stubCommon(fixture);
        when(groupStateRepository.findByMissionId(31L))
                .thenReturn(Optional.of(state));
        when(imageFileRepository.save(any(ImageFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(photoRepository.save(any(MissionPhoto.class)))
                .thenAnswer(invocation -> {
                    MissionPhoto photo = invocation.getArgument(0);
                    ReflectionTestUtils.setField(photo, "id", 41L);
                    return photo;
                });

        Long photoId = service.save(
                USER_PUBLIC_ID.toString(),
                31L,
                UUID.randomUUID(),
                "missions/31/photo.jpg",
                "photo.jpg",
                "image/jpeg",
                1024,
                BigDecimal.valueOf(35.8350),
                BigDecimal.valueOf(129.2188)
        );

        assertThat(photoId).isEqualTo(41L);
        ArgumentCaptor<MissionPhoto> captor =
                ArgumentCaptor.forClass(MissionPhoto.class);
        verify(photoRepository).save(captor.capture());
        MissionPhoto saved = captor.getValue();
        assertThat(saved.getMission()).isSameAs(fixture.mission());
        assertThat(saved.getSubmittedBy()).isSameAs(fixture.member());
        assertThat(saved.getParticipation()).isNull();
        assertThat(saved.getSubmittedAt()).isEqualTo(SEOUL_NOW);
        assertThat(saved.getImageFile().getCreatedAt()).isEqualTo(SEOUL_NOW);
        assertThat(saved.getImageFile().getImagePurpose())
                .isEqualTo(ImagePurpose.MISSION);
    }

    @DisplayName("완료한 단체 미션에는 대표 사진을 바꾸지 않고 사진을 추가한다")
    @Test
    void savesAdditionalGroupMissionPhoto() {
        Fixture fixture = fixture(MissionScope.GROUP);
        MissionGroupState state = MissionGroupState.create(
                fixture.mission(), LocalDateTime.now()
        );
        state.completeByAi(LocalDateTime.now());
        stubCommon(fixture);
        when(groupStateRepository.findByMissionId(31L))
                .thenReturn(Optional.of(state));
        when(imageFileRepository.save(any(ImageFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(photoRepository.save(any(MissionPhoto.class)))
                .thenAnswer(invocation -> {
                    MissionPhoto photo = invocation.getArgument(0);
                    ReflectionTestUtils.setField(photo, "id", 43L);
                    return photo;
                });

        Long photoId = service.saveAdditionalGroupPhoto(
                USER_PUBLIC_ID.toString(),
                31L,
                UUID.randomUUID(),
                "missions/31/additional.jpg",
                "additional.jpg",
                "image/jpeg",
                1024,
                null,
                null
        );

        assertThat(photoId).isEqualTo(43L);
        ArgumentCaptor<MissionPhoto> captor =
                ArgumentCaptor.forClass(MissionPhoto.class);
        verify(photoRepository).save(captor.capture());
        assertThat(captor.getValue().getRepresentativeSlot()).isNull();
    }

    @DisplayName("진행 중인 단체 미션에는 추가 사진 경로로 제출할 수 없다")
    @Test
    void rejectsAdditionalPhotoForPendingGroupMission() {
        Fixture fixture = fixture(MissionScope.GROUP);
        MissionGroupState state = MissionGroupState.create(
                fixture.mission(), LocalDateTime.now()
        );
        stubCommon(fixture);
        when(groupStateRepository.findByMissionId(31L))
                .thenReturn(Optional.of(state));

        assertThatThrownBy(() -> service.saveAdditionalGroupPhoto(
                USER_PUBLIC_ID.toString(),
                31L,
                UUID.randomUUID(),
                "missions/31/additional.jpg",
                "additional.jpg",
                "image/jpeg",
                1024,
                null,
                null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PHOTO_MISSION_SUBMISSION_NOT_ALLOWED);
        verify(imageFileRepository, never()).save(any());
    }

    @DisplayName("개인 미션 재촬영은 기존 활성 사진을 교체하고 새 사진을 저장한다")
    @Test
    void replacesActivePersonalPhoto() {
        Fixture fixture = fixture(MissionScope.PERSONAL);
        MissionParticipation participation = MissionParticipation.create(
                fixture.mission(), fixture.member(), LocalDateTime.now()
        );
        ReflectionTestUtils.setField(participation, "id", 61L);
        MissionPhoto previous = MissionPhoto.personal(
                participation,
                ImageFile.mission("old.jpg", "old.jpg", "image/jpeg", 10),
                UUID.randomUUID(),
                null,
                null,
                LocalDateTime.now().minusMinutes(1)
        );
        stubCommon(fixture);
        when(participationRepository.findByMissionIdAndTripMemberId(31L, 21L))
                .thenReturn(Optional.of(participation));
        when(photoRepository.findActiveByParticipationId(61L))
                .thenReturn(Optional.of(previous));
        when(imageFileRepository.save(any(ImageFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(photoRepository.save(any(MissionPhoto.class)))
                .thenAnswer(invocation -> {
                    MissionPhoto photo = invocation.getArgument(0);
                    ReflectionTestUtils.setField(photo, "id", 42L);
                    return photo;
                });

        Long photoId = service.save(
                USER_PUBLIC_ID.toString(),
                31L,
                UUID.randomUUID(),
                "missions/31/new.jpg",
                "new.jpg",
                "image/jpeg",
                1024,
                null,
                null
        );

        assertThat(photoId).isEqualTo(42L);
        assertThat(previous.getReplacedAt()).isNotNull();
        verify(photoRepository).flush();
    }

    @DisplayName("완료한 개인 미션의 재촬영 사진은 기존 사진을 유지한 채 판정 대기 상태로 저장한다")
    @Test
    void savesCompletedPersonalMissionPhotoAsCandidate() {
        Fixture fixture = fixture(MissionScope.PERSONAL);
        MissionParticipation participation = MissionParticipation.create(
                fixture.mission(), fixture.member(), LocalDateTime.now()
        );
        ReflectionTestUtils.setField(participation, "id", 61L);
        participation.completeByAi(LocalDateTime.now());
        MissionPhoto previous = MissionPhoto.personal(
                participation,
                ImageFile.mission("old.jpg", "old.jpg", "image/jpeg", 10),
                UUID.randomUUID(),
                null,
                null,
                LocalDateTime.now().minusMinutes(1)
        );
        stubCommon(fixture);
        when(participationRepository.findByMissionIdAndTripMemberId(31L, 21L))
                .thenReturn(Optional.of(participation));
        when(imageFileRepository.save(any(ImageFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(photoRepository.save(any(MissionPhoto.class)))
                .thenAnswer(invocation -> {
                    MissionPhoto photo = invocation.getArgument(0);
                    ReflectionTestUtils.setField(photo, "id", 42L);
                    return photo;
                });

        service.save(
                USER_PUBLIC_ID.toString(),
                31L,
                UUID.randomUUID(),
                "missions/31/new.jpg",
                "new.jpg",
                "image/jpeg",
                1024,
                null,
                null
        );

        ArgumentCaptor<MissionPhoto> captor =
                ArgumentCaptor.forClass(MissionPhoto.class);
        verify(photoRepository).save(captor.capture());
        assertThat(captor.getValue().getActiveSlot()).isNull();
        assertThat(previous.getReplacedAt()).isNull();
        assertThat(participation.getStatus())
                .isEqualTo(MissionCompletionStatus.COMPLETED);
        verify(photoRepository, never()).flush();
    }

    @DisplayName("이미 사용한 멱등성 키로 사진을 다시 제출할 수 없다")
    @Test
    void rejectsReusedIdempotencyKey() {
        UUID idempotencyKey = UUID.randomUUID();
        when(photoRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(mock(MissionPhoto.class)));

        assertThatThrownBy(() -> service.save(
                USER_PUBLIC_ID.toString(),
                31L,
                idempotencyKey,
                "missions/31/photo.jpg",
                "photo.jpg",
                "image/jpeg",
                1024,
                null,
                null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PHOTO_MISSION_IDEMPOTENCY_KEY_REUSED);
        verify(imageFileRepository, never()).save(any());
    }

    @DisplayName("아직 공개되지 않은 미래 Day 미션에는 사진을 제출할 수 없다")
    @Test
    void rejectsPhotoBeforeMissionPublication() {
        Fixture fixture = fixture(
                MissionScope.GROUP,
                TODAY.plusDays(1)
        );
        stubCommon(fixture);

        assertThatThrownBy(() -> service.save(
                USER_PUBLIC_ID.toString(),
                31L,
                UUID.randomUUID(),
                "missions/31/photo.jpg",
                "photo.jpg",
                "image/jpeg",
                1024,
                null,
                null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PHOTO_MISSION_SUBMISSION_NOT_ALLOWED);
        verify(imageFileRepository, never()).save(any());
    }

    @DisplayName("미션 당일 00시 30분 전에는 사진을 제출할 수 없다")
    @Test
    void rejectsPhotoBeforeDailyPublicationTime() {
        service = new PhotoMissionSubmissionTransactionService(
                missionRepository,
                participationRepository,
                groupStateRepository,
                photoRepository,
                userRepository,
                tripMemberRepository,
                imageFileRepository,
                fixedClock(LocalTime.of(0, 29, 59))
        );
        Fixture fixture = fixture(MissionScope.GROUP, TODAY);
        stubCommon(fixture);

        assertThatThrownBy(() -> service.save(
                USER_PUBLIC_ID.toString(),
                31L,
                UUID.randomUUID(),
                "missions/31/photo.jpg",
                "photo.jpg",
                "image/jpeg",
                1024,
                null,
                null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PHOTO_MISSION_SUBMISSION_NOT_ALLOWED);
        verify(imageFileRepository, never()).save(any());
    }

    @DisplayName("미션 당일 00시 30분부터 사진을 제출할 수 있다")
    @Test
    void acceptsPhotoAtDailyPublicationTime() {
        service = new PhotoMissionSubmissionTransactionService(
                missionRepository,
                participationRepository,
                groupStateRepository,
                photoRepository,
                userRepository,
                tripMemberRepository,
                imageFileRepository,
                fixedClock(LocalTime.of(0, 30))
        );
        Fixture fixture = fixture(MissionScope.GROUP, TODAY);
        MissionGroupState state = MissionGroupState.create(
                fixture.mission(), LocalDateTime.now()
        );
        stubCommon(fixture);
        when(groupStateRepository.findByMissionId(31L))
                .thenReturn(Optional.of(state));
        when(imageFileRepository.save(any(ImageFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(photoRepository.save(any(MissionPhoto.class)))
                .thenAnswer(invocation -> {
                    MissionPhoto photo = invocation.getArgument(0);
                    ReflectionTestUtils.setField(photo, "id", 43L);
                    return photo;
                });

        Long photoId = service.save(
                USER_PUBLIC_ID.toString(),
                31L,
                UUID.randomUUID(),
                "missions/31/photo.jpg",
                "photo.jpg",
                "image/jpeg",
                1024,
                null,
                null
        );

        assertThat(photoId).isEqualTo(43L);
        verify(imageFileRepository).save(any(ImageFile.class));
    }

    private void stubCommon(Fixture fixture) {
        when(photoRepository.findByIdempotencyKey(any()))
                .thenReturn(Optional.empty());
        when(missionRepository.findById(31L))
                .thenReturn(Optional.of(fixture.mission()));
        when(userRepository.findByPublicIdAndDeletedAtIsNull(USER_PUBLIC_ID))
                .thenReturn(Optional.of(fixture.user()));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                fixture.trip(), fixture.user()
        )).thenReturn(Optional.of(fixture.member()));
    }

    private Fixture fixture(MissionScope scope) {
        return fixture(scope, TODAY.minusDays(1));
    }

    private Fixture fixture(MissionScope scope, LocalDate scheduleDate) {
        Trip trip = mock(Trip.class);
        when(trip.getEndDate()).thenReturn(TODAY.plusDays(1));
        User user = new User(null, USER_PUBLIC_ID, "여행자");
        TripMember member = TripMember.createMember(trip, user);
        ReflectionTestUtils.setField(member, "id", 21L);
        Schedule schedule = mock(Schedule.class);
        ScheduleDay day = mock(ScheduleDay.class);
        ScheduleVisit visit = mock(ScheduleVisit.class);
        when(visit.getDay()).thenReturn(day);
        when(day.getSchedule()).thenReturn(schedule);
        when(day.getScheduleDate()).thenReturn(scheduleDate);
        when(schedule.getTrip()).thenReturn(trip);
        Mission mission = Mission.create(
                visit,
                1,
                scope,
                "포토미션",
                null,
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(mission, "id", 31L);
        return new Fixture(trip, user, member, mission);
    }

    private Clock fixedClock(LocalTime time) {
        return Clock.fixed(
                LocalDateTime.of(TODAY, time)
                        .atZone(SEOUL_ZONE)
                        .toInstant(),
                SEOUL_ZONE
        );
    }

    private record Fixture(
            Trip trip,
            User user,
            TripMember member,
            Mission mission
    ) {
    }
}
