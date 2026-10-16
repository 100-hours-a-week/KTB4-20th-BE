package com.planit.photomission.service;

import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.image.storage.ImageStorage;
import com.planit.photomission.domain.Mission;
import com.planit.photomission.domain.MissionCompletionStatus;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionParticipation;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.dto.PhotoMissionListResponse;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionParticipationRepository;
import com.planit.photomission.repository.MissionPhotoRepository;
import com.planit.photomission.repository.MissionRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhotoMissionQueryService {

    static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime DAILY_PUBLISH_TIME = LocalTime.of(0, 30);

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final MissionRepository missionRepository;
    private final MissionParticipationRepository participationRepository;
    private final MissionGroupStateRepository groupStateRepository;
    private final MissionPhotoRepository photoRepository;
    private final ImageStorage imageStorage;

    public PhotoMissionListResponse getMissionList(
            String userPublicId,
            Long tripId,
            Integer requestedDay
    ) {
        LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
        User user = requireUser(userPublicId);
        Trip trip = requireTrip(tripId);
        List<TripMember> activeMembers =
                tripMemberRepository.findActiveMembersByTrip(trip);
        TripMember viewer = requireActiveMember(activeMembers, user);
        int totalDays = tripDays(trip);
        int selectedDayNumber = resolveSelectedDay(
                requestedDay,
                trip,
                now.toLocalDate(),
                totalDays
        );
        LocalDate selectedDate = trip.getStartDate()
                .plusDays(selectedDayNumber - 1L);
        boolean published = isPublished(selectedDate, now);
        List<PhotoMissionListResponse.DayTab> days = dayTabs(
                trip,
                selectedDayNumber,
                totalDays,
                now
        );

        List<PhotoMissionListResponse.MissionCard> cards = published
                ? missionCards(
                        trip,
                        selectedDayNumber,
                        viewer,
                        activeMembers
                )
                : List.of();
        int completedCount = (int) cards.stream()
                .filter(card -> MissionCompletionStatus.COMPLETED.name()
                        .equals(card.status()))
                .count();

        return new PhotoMissionListResponse(
                new PhotoMissionListResponse.TripSummary(
                        trip.getId(),
                        trip.getName(),
                        trip.getStartDate(),
                        trip.getEndDate(),
                        activeMembers.size()
                ),
                days,
                new PhotoMissionListResponse.SelectedDay(
                        selectedDayNumber,
                        selectedDate,
                        published,
                        completedCount,
                        cards.size(),
                        !cards.isEmpty() && completedCount == cards.size(),
                        selectedDayNumber < totalDays,
                        cards
                )
        );
    }

    private List<PhotoMissionListResponse.MissionCard> missionCards(
            Trip trip,
            int dayNumber,
            TripMember viewer,
            List<TripMember> activeMembers
    ) {
        List<Mission> missions = missionRepository
                .findByTripIdAndScheduleDayDayNumberOrderByMissionOrderAsc(
                        trip.getId(),
                        (byte) dayNumber
                );
        if (missions.isEmpty()) {
            return List.of();
        }

        List<Long> missionIds = missions.stream().map(Mission::getId).toList();
        List<MissionParticipation> participations = participationRepository
                .findByMissionIdIn(missionIds);
        Map<Long, List<MissionParticipation>> participationsByMission =
                participations.stream().collect(Collectors.groupingBy(
                        participation -> participation.getMission().getId()
                ));
        Map<Long, MissionGroupState> groupStateByMission = groupStateRepository
                .findByMissionIdIn(missionIds)
                .stream()
                .collect(Collectors.toMap(
                        state -> state.getMission().getId(),
                        Function.identity()
                ));
        Map<Long, List<MissionPhoto>> photosByMission = photoRepository
                .findVisibleByMissionIdIn(missionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        photo -> photo.getMission().getId()
                ));
        Set<Long> activeMemberIds = activeMembers.stream()
                .map(TripMember::getId)
                .collect(Collectors.toSet());

        List<PhotoMissionListResponse.MissionCard> cards = new ArrayList<>();
        for (Mission mission : missions) {
            List<MissionParticipation> missionParticipations =
                    participationsByMission.getOrDefault(
                            mission.getId(),
                            List.of()
                    );
            List<MissionPhoto> missionPhotos = photosByMission.getOrDefault(
                    mission.getId(),
                    List.of()
            );
            cards.add(mission.isPersonal()
                    ? personalCard(
                            mission,
                            viewer,
                            activeMemberIds,
                            missionParticipations,
                            missionPhotos
                    )
                    : groupCard(
                            mission,
                            requireGroupState(
                                    groupStateByMission,
                                    mission.getId()
                            ),
                            missionPhotos
                    ));
        }
        return List.copyOf(cards);
    }

    private PhotoMissionListResponse.MissionCard personalCard(
            Mission mission,
            TripMember viewer,
            Set<Long> activeMemberIds,
            List<MissionParticipation> participations,
            List<MissionPhoto> photos
    ) {
        MissionParticipation mine = participations.stream()
                .filter(participation -> participation.getTripMember().getId()
                        .equals(viewer.getId()))
                .findFirst()
                .orElseThrow(this::inconsistentState);
        int completedMembers = (int) participations.stream()
                .filter(participation -> activeMemberIds.contains(
                        participation.getTripMember().getId()
                ))
                .filter(participation -> participation.getStatus()
                        == MissionCompletionStatus.COMPLETED)
                .count();
        String thumbnailUrl = photos.stream()
                .filter(photo -> belongsTo(photo, viewer))
                .findFirst()
                .map(this::imageUrl)
                .orElse(null);

        return card(
                mission,
                mine.getStatus(),
                thumbnailUrl,
                completedMembers,
                activeMemberIds.size(),
                null
        );
    }

    private PhotoMissionListResponse.MissionCard groupCard(
            Mission mission,
            MissionGroupState state,
            List<MissionPhoto> photos
    ) {
        MissionPhoto representative = photos.stream()
                .filter(this::isRepresentative)
                .findFirst()
                .orElse(null);
        PhotoMissionListResponse.CompletedBy completedBy =
                state.getStatus() == MissionCompletionStatus.COMPLETED
                        && representative != null
                        ? new PhotoMissionListResponse.CompletedBy(
                                representative.getSubmittedBy()
                                        .getUser().getPublicId(),
                                representative.getSubmittedBy()
                                        .getUser().getUsername(),
                                state.getCompletedAt()
                        )
                        : null;

        return card(
                mission,
                state.getStatus(),
                representative == null ? null : imageUrl(representative),
                null,
                null,
                completedBy
        );
    }

    private PhotoMissionListResponse.MissionCard card(
            Mission mission,
            MissionCompletionStatus status,
            String thumbnailUrl,
            Integer completedMemberCount,
            Integer totalMemberCount,
            PhotoMissionListResponse.CompletedBy completedBy
    ) {
        return new PhotoMissionListResponse.MissionCard(
                mission.getId(),
                mission.getMissionOrder(),
                mission.getScope().name(),
                mission.getDescription(),
                mission.getPrimaryCategory(),
                status.name(),
                thumbnailUrl,
                completedMemberCount,
                totalMemberCount,
                completedBy
        );
    }

    private User requireUser(String userPublicId) {
        return userRepository.findByPublicIdAndDeletedAtIsNull(
                        UUID.fromString(userPublicId)
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED
                ));
    }

    private Trip requireTrip(Long tripId) {
        return tripRepository.findById(tripId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_NOT_FOUND
                ));
    }

    private TripMember requireActiveMember(
            List<TripMember> activeMembers,
            User user
    ) {
        return activeMembers.stream()
                .filter(member -> member.getUser().getPublicId()
                        .equals(user.getPublicId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_MEMBER_REQUIRED
                ));
    }

    private int resolveSelectedDay(
            Integer requestedDay,
            Trip trip,
            LocalDate today,
            int totalDays
    ) {
        if (requestedDay != null) {
            if (requestedDay < 1 || requestedDay > totalDays) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            return requestedDay;
        }
        if (today.isBefore(trip.getStartDate())) {
            return 1;
        }
        if (today.isAfter(trip.getEndDate())) {
            return totalDays;
        }
        return (int) ChronoUnit.DAYS.between(
                trip.getStartDate(),
                today
        ) + 1;
    }

    private List<PhotoMissionListResponse.DayTab> dayTabs(
            Trip trip,
            int selectedDay,
            int totalDays,
            LocalDateTime now
    ) {
        List<PhotoMissionListResponse.DayTab> days = new ArrayList<>();
        for (int day = 1; day <= totalDays; day++) {
            LocalDate date = trip.getStartDate().plusDays(day - 1L);
            days.add(new PhotoMissionListResponse.DayTab(
                    day,
                    date,
                    isPublished(date, now),
                    day == selectedDay
            ));
        }
        return List.copyOf(days);
    }

    private int tripDays(Trip trip) {
        return (int) ChronoUnit.DAYS.between(
                trip.getStartDate(),
                trip.getEndDate()
        ) + 1;
    }

    private boolean isPublished(LocalDate date, LocalDateTime now) {
        return !now.isBefore(date.atTime(DAILY_PUBLISH_TIME));
    }

    private MissionGroupState requireGroupState(
            Map<Long, MissionGroupState> states,
            Long missionId
    ) {
        MissionGroupState state = states.get(missionId);
        if (state == null) {
            throw inconsistentState();
        }
        return state;
    }

    private boolean belongsTo(MissionPhoto photo, TripMember member) {
        return photo.getParticipation() != null
                && photo.getSubmittedBy().getId()
                .equals(member.getId());
    }

    private boolean isRepresentative(MissionPhoto photo) {
        return photo.getRepresentativeSlot() != null
                && photo.getRepresentativeSlot() == 1;
    }

    private String imageUrl(MissionPhoto photo) {
        return imageStorage.createReadUrl(
                photo.getImageFile().getImageKey()
        );
    }

    private BusinessException inconsistentState() {
        return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

}
