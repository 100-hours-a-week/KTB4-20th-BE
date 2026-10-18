package com.planit.photomission.service;

import com.planit.domain.ImageFile;
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
import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.dto.PhotoMissionAlbumResponse;
import com.planit.photomission.dto.PhotoMissionCompletionResponse;
import com.planit.photomission.dto.PhotoMissionDetailResponse;
import com.planit.photomission.dto.PhotoMissionListResponse;
import com.planit.photomission.dto.PhotoMissionPhotoDetailResponse;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionParticipationRepository;
import com.planit.photomission.repository.MissionPhotoRepository;
import com.planit.photomission.repository.MissionRepository;
import com.planit.photomission.repository.PhotoEvaluationRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.domain.Place;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhotoMissionQueryService {

    static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final MissionRepository missionRepository;
    private final MissionParticipationRepository participationRepository;
    private final MissionGroupStateRepository groupStateRepository;
    private final MissionPhotoRepository photoRepository;
    private final PhotoEvaluationRepository evaluationRepository;
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

    public PhotoMissionDetailResponse getMissionDetail(
            String userPublicId,
            Long missionId
    ) {
        LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
        User user = requireUser(userPublicId);
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND
                ));
        Trip trip = mission.getTrip();
        requireActiveTrip(trip);
        if (!isPublished(mission.getScheduleDay().getScheduleDate(), now)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        List<TripMember> activeMembers = tripMemberRepository
                .findActiveMembersByTrip(trip);
        TripMember viewer = requireActiveMember(activeMembers, user);
        List<MissionParticipation> participations = mission.isPersonal()
                ? participationRepository.findByMissionIdIn(List.of(missionId))
                : List.of();
        MissionGroupState groupState = mission.isGroup()
                ? groupStateRepository.findByMissionId(missionId)
                        .orElseThrow(this::inconsistentState)
                : null;
        Map<Long, MissionParticipation> participationByMember = participations
                .stream()
                .collect(Collectors.toMap(
                        participation -> participation.getTripMember().getId(),
                        Function.identity()
                ));

        MissionCompletionStatus status;
        int retryCount;
        String completionMethod;
        LocalDateTime completedAt;
        if (mission.isPersonal()) {
            MissionParticipation mine = requireParticipation(
                    participationByMember,
                    viewer.getId()
            );
            status = mine.getStatus();
            retryCount = mine.getRetryCount();
            completionMethod = mine.getCompletionMethod() == null
                    ? null
                    : mine.getCompletionMethod().name();
            completedAt = mine.getCompletedAt();
        } else {
            status = groupState.getStatus();
            retryCount = groupState.getRetryCount();
            completionMethod = groupState.getCompletionMethod() == null
                    ? null
                    : groupState.getCompletionMethod().name();
            completedAt = groupState.getCompletedAt();
        }

        List<PhotoMissionDetailResponse.MemberStatus> members = activeMembers
                .stream()
                .map(member -> memberStatus(
                        member,
                        viewer,
                        mission,
                        participationByMember,
                        groupState
                ))
                .toList();
        List<PhotoMissionDetailResponse.Photo> photos = photoRepository
                .findVisibleByMissionIdIn(List.of(missionId))
                .stream()
                .filter(photo -> mission.isGroup()
                        || belongsTo(photo, viewer))
                .sorted(photoOrder())
                .map(this::photoResponse)
                .toList();

        return new PhotoMissionDetailResponse(
                mission.getId(),
                mission.getScheduleDay().getDayNumber(),
                mission.getScheduleDay().getScheduleDate(),
                mission.getScope().name(),
                mission.getDescription(),
                mission.getPrimaryCategory(),
                mission.isExpired(now),
                status.name(),
                retryCount,
                completionMethod,
                completedAt,
                members,
                photos
        );
    }

    public PhotoMissionPhotoDetailResponse getPhotoDetail(
            String userPublicId,
            Long missionId,
            Long photoId
    ) {
        LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
        User user = requireUser(userPublicId);
        MissionPhoto photo = photoRepository
                .findVisibleDetailByMissionIdAndPhotoId(missionId, photoId)
                .orElseThrow(this::resourceNotFound);
        Mission mission = photo.getMission();
        TripMember viewer = requireActiveMember(
                tripMemberRepository.findActiveMembersByTrip(
                        mission.getTrip()
                ),
                user
        );
        if (!isPublished(mission.getScheduleDay().getScheduleDate(), now)
                || !isCompletedPhotoVisibleTo(photo, viewer)) {
            throw resourceNotFound();
        }

        User submitter = photo.getSubmittedBy().getUser();
        Place place = mission.getScheduleVisit().getPlace();
        return new PhotoMissionPhotoDetailResponse(
                photo.getId(),
                imageUrl(photo),
                photo.getSubmittedAt(),
                isRepresentative(photo),
                new PhotoMissionPhotoDetailResponse.MissionSummary(
                        mission.getId(),
                        mission.getScope().name(),
                        mission.getDescription(),
                        mission.getPrimaryCategory()
                ),
                new PhotoMissionPhotoDetailResponse.PlaceSummary(
                        place.getGooglePlaceId(),
                        place.getName()
                ),
                new PhotoMissionPhotoDetailResponse.SubmittedBy(
                        submitter.getPublicId(),
                        submitter.getUsername()
                )
        );
    }

    public PhotoMissionAlbumResponse getAlbum(
            String userPublicId,
            Long tripId
    ) {
        LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
        User user = requireUser(userPublicId);
        Trip trip = requireTrip(tripId);
        TripMember viewer = requireActiveMember(
                tripMemberRepository.findActiveMembersByTrip(trip),
                user
        );
        List<Mission> missions = missionRepository
                .findByTripIdOrderByDayAndMissionOrder(tripId)
                .stream()
                .filter(mission -> isPublished(
                        mission.getScheduleDay().getScheduleDate(),
                        now
                ))
                .toList();
        if (missions.isEmpty()) {
            return emptyAlbum(trip);
        }

        List<Long> missionIds = missions.stream().map(Mission::getId).toList();
        Map<Long, List<MissionParticipation>> participationsByMission =
                participationRepository.findByMissionIdIn(missionIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                participation -> participation.getMission()
                                        .getId()
                        ));
        Map<Long, MissionGroupState> groupStateByMission =
                groupStateRepository.findByMissionIdIn(missionIds)
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
        Set<Long> successfulPhotoIds = successfulPhotoIds(photosByMission);

        Map<Integer, List<PhotoMissionAlbumResponse.MissionAlbum>> personal =
                new LinkedHashMap<>();
        Map<Integer, List<PhotoMissionAlbumResponse.MissionAlbum>> group =
                new LinkedHashMap<>();
        Set<Integer> includedDays = new TreeSet<>();
        int totalPhotos = 0;
        int completedMissions = 0;

        for (Mission mission : missions) {
            List<MissionPhoto> photos = completedAlbumPhotos(
                    mission,
                    viewer,
                    participationsByMission.getOrDefault(
                            mission.getId(),
                            List.of()
                    ),
                    groupStateByMission.get(mission.getId()),
                    photosByMission.getOrDefault(mission.getId(), List.of()),
                    successfulPhotoIds
            );
            if (photos.isEmpty()) {
                continue;
            }

            PhotoMissionAlbumResponse.MissionAlbum card = albumCard(
                    mission,
                    photos
            );
            int day = mission.getScheduleDay().getDayNumber();
            Map<Integer, List<PhotoMissionAlbumResponse.MissionAlbum>> target =
                    mission.isPersonal() ? personal : group;
            target.computeIfAbsent(day, ignored -> new ArrayList<>()).add(card);
            includedDays.add(day);
            completedMissions++;
            totalPhotos += photos.size();
        }

        List<PhotoMissionAlbumResponse.DayAlbum> days = includedDays.stream()
                .map(day -> new PhotoMissionAlbumResponse.DayAlbum(
                        day,
                        trip.getStartDate().plusDays(day - 1L),
                        List.copyOf(personal.getOrDefault(day, List.of())),
                        List.copyOf(group.getOrDefault(day, List.of()))
                ))
                .toList();
        return new PhotoMissionAlbumResponse(
                trip.getId(),
                trip.getName(),
                completedMissions,
                totalPhotos,
                days
        );
    }

    public PhotoMissionCompletionResponse getCompletion(
            String userPublicId,
            Long tripId,
            Integer day
    ) {
        LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
        User user = requireUser(userPublicId);
        Trip trip = requireTrip(tripId);
        TripMember viewer = requireActiveMember(
                tripMemberRepository.findActiveMembersByTrip(trip),
                user
        );
        int totalDays = tripDays(trip);
        if (day != null && (day < 1 || day > totalDays)) {
            throw new BusinessException(ErrorCode.SCHEDULE_DAY_NOT_FOUND);
        }
        if (day != null && !isPublished(
                trip.getStartDate().plusDays(day - 1L),
                now
        )) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        List<Mission> missions = day == null
                ? missionRepository.findByTripIdOrderByDayAndMissionOrder(tripId)
                : missionRepository
                        .findByTripIdAndScheduleDayDayNumberOrderByMissionOrderAsc(
                                tripId,
                                day.byteValue()
                        );
        return completionResponse(trip, viewer, day, missions, now);
    }

    private PhotoMissionCompletionResponse completionResponse(
            Trip trip,
            TripMember viewer,
            Integer day,
            List<Mission> missions,
            LocalDateTime now
    ) {
        if (missions.isEmpty()) {
            return emptyCompletion(trip, day, now);
        }

        List<Long> missionIds = missions.stream().map(Mission::getId).toList();
        Map<Long, List<MissionParticipation>> participationsByMission =
                participationRepository.findByMissionIdIn(missionIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                participation -> participation.getMission()
                                        .getId()
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
        Set<Long> successfulPhotoIds = successfulPhotoIds(photosByMission);

        List<MissionPhoto> completedPhotos = new ArrayList<>();
        int completedMissionCount = 0;
        for (Mission mission : missions) {
            List<MissionParticipation> participations =
                    participationsByMission.getOrDefault(
                            mission.getId(),
                            List.of()
                    );
            MissionGroupState groupState = groupStateByMission.get(
                    mission.getId()
            );
            if (!completedForViewer(
                    mission,
                    viewer,
                    participations,
                    groupState
            )) {
                continue;
            }
            completedMissionCount++;
            completedPhotos.addAll(visiblePhotosForViewer(
                    mission,
                    viewer,
                    photosByMission.getOrDefault(mission.getId(), List.of()),
                    groupState,
                    successfulPhotoIds
            ));
        }
        completedPhotos.sort(completionPhotoOrder());

        int landmarkCount = recognizedLandmarkCount(completedPhotos);
        int totalMissionCount = missions.size();
        List<PhotoMissionCompletionResponse.PreviewPhoto> previews =
                completedPhotos.stream()
                        .limit(4)
                        .map(photo -> new PhotoMissionCompletionResponse
                                .PreviewPhoto(photo.getId(), imageUrl(photo)))
                        .toList();
        return new PhotoMissionCompletionResponse(
                trip.getId(),
                trip.getName(),
                trip.getStartDate(),
                trip.getEndDate(),
                day == null ? "TRIP" : "DAY",
                day,
                day == null ? null : trip.getStartDate().plusDays(day - 1L),
                completedMissionCount,
                totalMissionCount,
                totalMissionCount - completedMissionCount,
                completedPhotos.size(),
                landmarkCount,
                totalMissionCount > 0
                        && completedMissionCount == totalMissionCount,
                now.toLocalDate().isAfter(trip.getEndDate()),
                day != null && day < tripDays(trip),
                previews
        );
    }

    private PhotoMissionCompletionResponse emptyCompletion(
            Trip trip,
            Integer day,
            LocalDateTime now
    ) {
        return new PhotoMissionCompletionResponse(
                trip.getId(),
                trip.getName(),
                trip.getStartDate(),
                trip.getEndDate(),
                day == null ? "TRIP" : "DAY",
                day,
                day == null ? null : trip.getStartDate().plusDays(day - 1L),
                0,
                0,
                0,
                0,
                0,
                false,
                now.toLocalDate().isAfter(trip.getEndDate()),
                day != null && day < tripDays(trip),
                List.of()
        );
    }

    private int recognizedLandmarkCount(List<MissionPhoto> photos) {
        if (photos.isEmpty()) {
            return 0;
        }
        Map<Long, MissionPhoto> photoById = photos.stream().collect(
                Collectors.toMap(MissionPhoto::getId, Function.identity())
        );
        return (int) evaluationRepository
                .findRecognizedByMissionPhotoIdIn(
                        photoById.keySet().stream().sorted().toList()
                )
                .stream()
                .map(PhotoEvaluation::getMissionPhoto)
                .map(MissionPhoto::getId)
                .map(photoById::get)
                .map(photo -> photo.getMission().getScheduleVisit()
                        .getPlace().getGooglePlaceId())
                .distinct()
                .count();
    }

    private PhotoMissionAlbumResponse emptyAlbum(Trip trip) {
        return new PhotoMissionAlbumResponse(
                trip.getId(),
                trip.getName(),
                0,
                0,
                List.of()
        );
    }

    private List<MissionPhoto> completedAlbumPhotos(
            Mission mission,
            TripMember viewer,
            List<MissionParticipation> participations,
            MissionGroupState groupState,
            List<MissionPhoto> photos,
            Set<Long> successfulPhotoIds
    ) {
        if (!completedForViewer(
                mission,
                viewer,
                participations,
                groupState
        )) {
            return List.of();
        }
        return visiblePhotosForViewer(
                mission,
                viewer,
                photos,
                groupState,
                successfulPhotoIds
        );
    }

    private boolean completedForViewer(
            Mission mission,
            TripMember viewer,
            List<MissionParticipation> participations,
            MissionGroupState groupState
    ) {
        if (mission.isPersonal()) {
            return participations.stream()
                    .filter(participation -> participation.getTripMember()
                            .getId().equals(viewer.getId()))
                    .anyMatch(participation -> participation.getStatus()
                            == MissionCompletionStatus.COMPLETED);
        }
        return groupState != null && groupState.getStatus()
                == MissionCompletionStatus.COMPLETED;
    }

    private boolean isCompletedPhotoVisibleTo(
            MissionPhoto photo,
            TripMember viewer
    ) {
        Mission mission = photo.getMission();
        if (mission.isPersonal()) {
            return belongsTo(photo, viewer)
                    && photo.getParticipation().getStatus()
                    == MissionCompletionStatus.COMPLETED;
        }
        MissionGroupState state = groupStateRepository
                .findByMissionId(mission.getId())
                .orElse(null);
        if (state == null
                || state.getStatus() != MissionCompletionStatus.COMPLETED) {
            return false;
        }
        Set<Long> successfulPhotoIds = Set.copyOf(
                evaluationRepository.findSuccessfulMissionPhotoIds(
                        List.of(photo.getId())
                )
        );
        return isVisibleGroupCompletionPhoto(
                photo,
                state,
                successfulPhotoIds
        );
    }

    private List<MissionPhoto> visiblePhotosForViewer(
            Mission mission,
            TripMember viewer,
            List<MissionPhoto> photos,
            MissionGroupState groupState,
            Set<Long> successfulPhotoIds
    ) {
        return photos.stream()
                .filter(photo -> mission.isGroup() || belongsTo(photo, viewer))
                .filter(photo -> mission.isPersonal()
                        || isVisibleGroupCompletionPhoto(
                                photo,
                                groupState,
                                successfulPhotoIds
                        ))
                .sorted(completionPhotoOrder())
                .toList();
    }

    private boolean isVisibleGroupCompletionPhoto(
            MissionPhoto photo,
            MissionGroupState groupState,
            Set<Long> successfulPhotoIds
    ) {
        return isRepresentative(photo)
                || successfulPhotoIds.contains(photo.getId())
                || groupState != null
                && groupState.getCompletedAt() != null
                && !photo.getSubmittedAt().isBefore(
                        groupState.getCompletedAt()
                );
    }

    private Set<Long> successfulPhotoIds(
            Map<Long, List<MissionPhoto>> photosByMission
    ) {
        List<Long> photoIds = photosByMission.values().stream()
                .flatMap(List::stream)
                .map(MissionPhoto::getId)
                .sorted()
                .toList();
        if (photoIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(
                evaluationRepository.findSuccessfulMissionPhotoIds(photoIds)
        );
    }

    private PhotoMissionAlbumResponse.MissionAlbum albumCard(
            Mission mission,
            List<MissionPhoto> photos
    ) {
        MissionPhoto thumbnail = photos.get(0);
        User submitter = thumbnail.getSubmittedBy().getUser();
        return new PhotoMissionAlbumResponse.MissionAlbum(
                mission.getId(),
                mission.getScope().name(),
                mission.getDescription(),
                mission.getPrimaryCategory(),
                imageUrl(thumbnail),
                photos.size(),
                new PhotoMissionAlbumResponse.SubmittedBy(
                        submitter.getPublicId(),
                        submitter.getUsername()
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

    private PhotoMissionDetailResponse.MemberStatus memberStatus(
            TripMember member,
            TripMember viewer,
            Mission mission,
            Map<Long, MissionParticipation> participationByMember,
            MissionGroupState groupState
    ) {
        MissionCompletionStatus status;
        LocalDateTime completedAt;
        if (mission.isPersonal()) {
            MissionParticipation participation = requireParticipation(
                    participationByMember,
                    member.getId()
            );
            status = participation.getStatus();
            completedAt = participation.getCompletedAt();
        } else {
            status = groupState.getStatus();
            completedAt = groupState.getCompletedAt();
        }
        User user = member.getUser();
        return new PhotoMissionDetailResponse.MemberStatus(
                member.getId(),
                user.getPublicId(),
                user.getUsername(),
                profileImageUrl(user),
                member.getId().equals(viewer.getId()),
                status.name(),
                completedAt
        );
    }

    private PhotoMissionDetailResponse.Photo photoResponse(MissionPhoto photo) {
        User submitter = photo.getSubmittedBy().getUser();
        return new PhotoMissionDetailResponse.Photo(
                photo.getId(),
                imageUrl(photo),
                submitter.getPublicId(),
                submitter.getUsername(),
                isRepresentative(photo),
                photo.getSubmittedAt()
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

    private void requireActiveTrip(Trip trip) {
        if (trip.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
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
        return PhotoMissionAvailabilityPolicy.isPublished(date, now);
    }

    private MissionParticipation requireParticipation(
            Map<Long, MissionParticipation> participationByMember,
            Long memberId
    ) {
        MissionParticipation participation =
                participationByMember.get(memberId);
        if (participation == null) {
            throw inconsistentState();
        }
        return participation;
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

    private Comparator<MissionPhoto> photoOrder() {
        return Comparator
                .comparing(this::isRepresentative)
                .reversed()
                .thenComparing(MissionPhoto::getSubmittedAt)
                .thenComparing(MissionPhoto::getId);
    }

    private Comparator<MissionPhoto> completionPhotoOrder() {
        return Comparator.comparing(MissionPhoto::getSubmittedAt)
                .thenComparing(MissionPhoto::getId);
    }

    private String imageUrl(MissionPhoto photo) {
        return imageStorage.createReadUrl(
                photo.getImageFile().getImageKey()
        );
    }

    private String profileImageUrl(User user) {
        ImageFile imageFile = user.getImageFile();
        return imageFile == null
                ? null
                : imageStorage.createReadUrl(imageFile.getImageKey());
    }

    private BusinessException inconsistentState() {
        return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private BusinessException resourceNotFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

}
