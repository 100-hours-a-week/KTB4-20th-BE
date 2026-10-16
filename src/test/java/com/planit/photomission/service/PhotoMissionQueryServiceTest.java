package com.planit.photomission.service;

import com.planit.domain.ImageFile;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.image.storage.ImageStorage;
import com.planit.photomission.domain.Mission;
import com.planit.photomission.domain.MissionCompletionMethod;
import com.planit.photomission.domain.MissionCompletionStatus;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionParticipation;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.domain.MissionScope;
import com.planit.photomission.dto.PhotoMissionListResponse;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionParticipationRepository;
import com.planit.photomission.repository.MissionPhotoRepository;
import com.planit.photomission.repository.MissionRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.domain.ScheduleDay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("포토미션 목록·상세 조회 서비스")
class PhotoMissionQueryServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripMemberRepository tripMemberRepository =
            mock(TripMemberRepository.class);
    private final MissionRepository missionRepository =
            mock(MissionRepository.class);
    private final MissionParticipationRepository participationRepository =
            mock(MissionParticipationRepository.class);
    private final MissionGroupStateRepository groupStateRepository =
            mock(MissionGroupStateRepository.class);
    private final MissionPhotoRepository photoRepository =
            mock(MissionPhotoRepository.class);
    private final ImageStorage imageStorage = mock(ImageStorage.class);

    private PhotoMissionQueryService service;
    private User me;
    private Trip trip;
    private TripMember myMember;
    private TripMember otherMember;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        service = new PhotoMissionQueryService(
                userRepository,
                tripRepository,
                tripMemberRepository,
                missionRepository,
                participationRepository,
                groupStateRepository,
                photoRepository,
                imageStorage
        );
        today = LocalDate.now(PhotoMissionQueryService.SEOUL_ZONE);
        me = mock(User.class);
        trip = mock(Trip.class);
        myMember = member(1L, me, "나");
        otherMember = member(2L, mock(User.class), "친구");

        when(userRepository.findByPublicIdAndDeletedAtIsNull(any()))
                .thenReturn(Optional.of(me));
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(trip.getId()).thenReturn(10L);
        when(trip.getName()).thenReturn("경주 여행");
        when(trip.getStartDate()).thenReturn(today.minusDays(1));
        when(trip.getEndDate()).thenReturn(today.plusDays(1));
        when(tripMemberRepository.findActiveMembersByTrip(trip))
                .thenReturn(List.of(myMember, otherMember));
    }

    @DisplayName("개인 미션은 내 상태로, 단체 미션은 공유 상태로 Day 진행률을 계산한다")
    @Test
    void getsMissionListUsingViewerAndGroupCompletion() {
        ScheduleDay day = day(1, today.minusDays(1));
        Mission personal = mission(
                101L, day, 1, MissionScope.PERSONAL, "유적지 전경 담기"
        );
        Mission group = mission(
                102L, day, 2, MissionScope.GROUP, "다 같이 그룹 사진 찍기"
        );
        MissionParticipation myParticipation = participation(
                201L, personal, myMember, MissionCompletionStatus.COMPLETED
        );
        MissionParticipation otherParticipation = participation(
                202L, personal, otherMember, MissionCompletionStatus.PENDING
        );
        MissionGroupState groupState = mock(MissionGroupState.class);
        when(groupState.getMission()).thenReturn(group);
        when(groupState.getStatus())
                .thenReturn(MissionCompletionStatus.COMPLETED);
        when(groupState.getCompletedAt())
                .thenReturn(LocalDateTime.of(today, java.time.LocalTime.NOON));
        MissionPhoto representative = photo(
                301L, group, otherMember, true, "group.jpg"
        );

        when(missionRepository
                .findByTripIdAndScheduleDayDayNumberOrderByMissionOrderAsc(
                        10L,
                        (byte) 1
                )).thenReturn(List.of(personal, group));
        when(participationRepository.findByMissionIdIn(List.of(101L, 102L)))
                .thenReturn(List.of(myParticipation, otherParticipation));
        when(groupStateRepository.findByMissionIdIn(List.of(101L, 102L)))
                .thenReturn(List.of(groupState));
        when(photoRepository.findVisibleByMissionIdIn(List.of(101L, 102L)))
                .thenReturn(List.of(representative));
        when(imageStorage.createReadUrl("group.jpg"))
                .thenReturn("https://image/group.jpg");

        PhotoMissionListResponse response = service.getMissionList(
                UUID.randomUUID().toString(),
                10L,
                1
        );

        assertThat(response.selectedDay().completedMissionCount()).isEqualTo(2);
        assertThat(response.selectedDay().totalMissionCount()).isEqualTo(2);
        assertThat(response.selectedDay().completed()).isTrue();
        assertThat(response.selectedDay().missions().get(0).status())
                .isEqualTo("COMPLETED");
        assertThat(response.selectedDay().missions().get(0).completedMemberCount())
                .isEqualTo(1);
        assertThat(response.selectedDay().missions().get(0).totalMemberCount())
                .isEqualTo(2);
        assertThat(response.selectedDay().missions().get(1).completedBy().username())
                .isEqualTo("친구");
        assertThat(response.selectedDay().missions().get(1).thumbnailUrl())
                .isEqualTo("https://image/group.jpg");
    }

    @DisplayName("아직 공개되지 않은 미래 Day는 미션을 반환하지 않는다")
    @Test
    void hidesFutureDayMissions() {
        PhotoMissionListResponse response = service.getMissionList(
                UUID.randomUUID().toString(),
                10L,
                3
        );

        assertThat(response.selectedDay().published()).isFalse();
        assertThat(response.selectedDay().missions()).isEmpty();
        assertThat(response.days().get(2).selectable()).isFalse();
    }

    private TripMember member(Long id, User user, String username) {
        TripMember member = mock(TripMember.class);
        when(member.getId()).thenReturn(id);
        when(member.getUser()).thenReturn(user);
        when(member.isActive()).thenReturn(true);
        when(user.getUsername()).thenReturn(username);
        when(user.getPublicId()).thenReturn(UUID.randomUUID());
        return member;
    }

    private ScheduleDay day(int number, LocalDate date) {
        ScheduleDay day = mock(ScheduleDay.class);
        when(day.getDayNumber()).thenReturn((byte) number);
        when(day.getScheduleDate()).thenReturn(date);
        return day;
    }

    private Mission mission(
            Long id,
            ScheduleDay day,
            int order,
            MissionScope scope,
            String description
    ) {
        Mission mission = mock(Mission.class);
        when(mission.getId()).thenReturn(id);
        when(mission.getTrip()).thenReturn(trip);
        when(mission.getScheduleDay()).thenReturn(day);
        when(mission.getMissionOrder()).thenReturn((byte) order);
        when(mission.getScope()).thenReturn(scope);
        when(mission.getDescription()).thenReturn(description);
        when(mission.isPersonal()).thenReturn(scope == MissionScope.PERSONAL);
        when(mission.isGroup()).thenReturn(scope == MissionScope.GROUP);
        when(mission.isExpired(any())).thenReturn(false);
        return mission;
    }

    private MissionParticipation participation(
            Long id,
            Mission mission,
            TripMember member,
            MissionCompletionStatus status
    ) {
        MissionParticipation participation = mock(MissionParticipation.class);
        when(participation.getId()).thenReturn(id);
        when(participation.getMission()).thenReturn(mission);
        when(participation.getTripMember()).thenReturn(member);
        when(participation.getStatus()).thenReturn(status);
        when(participation.getCompletionMethod())
                .thenReturn(status == MissionCompletionStatus.COMPLETED
                        ? MissionCompletionMethod.AI
                        : null);
        return participation;
    }

    private MissionPhoto photo(
            Long id,
            Mission mission,
            TripMember submitter,
            boolean representative,
            String imageKey
    ) {
        MissionPhoto photo = mock(MissionPhoto.class);
        ImageFile image = mock(ImageFile.class);
        when(photo.getId()).thenReturn(id);
        when(photo.getMission()).thenReturn(mission);
        when(photo.getSubmittedBy()).thenReturn(submitter);
        when(photo.getImageFile()).thenReturn(image);
        when(photo.getRepresentativeSlot())
                .thenReturn(representative ? (byte) 1 : null);
        when(photo.getSubmittedAt()).thenReturn(LocalDateTime.now());
        when(image.getImageKey()).thenReturn(imageKey);
        return photo;
    }
}
