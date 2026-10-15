package com.planit.photomission.service;

import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.photomission.ai.PhotoMissionGenerateRequest;
import com.planit.photomission.ai.PhotoMissionGenerateResponse;
import com.planit.photomission.domain.Mission;
import com.planit.photomission.domain.MissionGenerationJob;
import com.planit.photomission.domain.MissionGenerationStatus;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionParticipation;
import com.planit.photomission.domain.MissionScope;
import com.planit.photomission.repository.MissionGenerationJobRepository;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionParticipationRepository;
import com.planit.photomission.repository.MissionRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleVisit;
import com.planit.schedule.repository.ScheduleRepository;
import com.planit.schedule.repository.ScheduleVisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PhotoMissionGenerationTransactionService {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleVisitRepository visitRepository;
    private final MissionGenerationJobRepository jobRepository;
    private final MissionRepository missionRepository;
    private final MissionParticipationRepository participationRepository;
    private final MissionGroupStateRepository groupStateRepository;

    @Transactional
    public Long start(Long tripId, LocalDateTime attemptedAt) {
        Trip trip = tripRepository.findById(tripId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException(
                        "포토미션 생성 대상 여행을 찾을 수 없습니다"
                ));
        MissionGenerationJob job = jobRepository.findByTripId(tripId)
                .map(existing -> retry(existing, attemptedAt))
                .orElseGet(() -> MissionGenerationJob.start(
                        trip,
                        attemptedAt
                ));
        return jobRepository.save(job).getId();
    }

    @Transactional(readOnly = true)
    public PhotoMissionGenerateRequest loadRequest(Long jobId) {
        MissionGenerationJob job = findJob(jobId);
        Schedule schedule = findActiveSchedule(job.getTrip().getId());
        List<ScheduleVisit> visits = findActiveVisits(schedule.getId());
        ensureUniquePlaces(visits);

        return new PhotoMissionGenerateRequest(
                "trip_" + job.getTrip().getId(),
                visits.stream().map(this::toRequestPlace).toList()
        );
    }

    @Transactional
    public void complete(
            Long jobId,
            PhotoMissionGenerateResponse response,
            LocalDateTime completedAt
    ) {
        MissionGenerationJob job = findJob(jobId);
        requireRunning(job);
        Schedule schedule = findActiveSchedule(job.getTrip().getId());
        List<ScheduleVisit> visits = findActiveVisits(schedule.getId());
        Map<String, PhotoMissionGenerateResponse.Mission> missionsByPlace =
                validateAndIndex(response, visits);
        List<TripMember> members = tripMemberRepository
                .findActiveMembersByTrip(job.getTrip());

        for (ScheduleVisit visit : visits) {
            PhotoMissionGenerateResponse.Mission generated =
                    missionsByPlace.get(visit.getPlace().getGooglePlaceId());
            Mission mission = missionRepository.save(Mission.create(
                    visit,
                    visit.getVisitOrder(),
                    MissionScope.valueOf(generated.scope()),
                    generated.description(),
                    generated.primaryCategory(),
                    completedAt
            ));
            createState(mission, members, completedAt);
        }
        job.succeed();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long jobId) {
        MissionGenerationJob job = findJob(jobId);
        if (job.getStatus() == MissionGenerationStatus.RUNNING) {
            job.fail();
        }
    }

    private MissionGenerationJob retry(
            MissionGenerationJob job,
            LocalDateTime attemptedAt
    ) {
        if (job.getStatus() == MissionGenerationStatus.FAILED) {
            job.retry(attemptedAt);
        } else {
            job.retryStale(attemptedAt);
        }
        return job;
    }

    private MissionGenerationJob findJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "포토미션 생성 작업을 찾을 수 없습니다"
                ));
    }

    private Schedule findActiveSchedule(Long tripId) {
        return scheduleRepository
                .findByTripIdAndActiveConfirmedSlot(tripId, (byte) 1)
                .orElseThrow(() -> new IllegalStateException(
                        "확정된 여행 일정이 없습니다"
                ));
    }

    private List<ScheduleVisit> findActiveVisits(Long scheduleId) {
        List<ScheduleVisit> visits = visitRepository
                .findByDay_Schedule_IdAndStatusOrderByDay_DayNumberAscVisitOrderAsc(
                        scheduleId,
                        "ACTIVE"
                );
        if (visits.isEmpty()) {
            throw new IllegalStateException("포토미션 생성 대상 장소가 없습니다");
        }
        return visits;
    }

    private PhotoMissionGenerateRequest.Place toRequestPlace(
            ScheduleVisit visit
    ) {
        return new PhotoMissionGenerateRequest.Place(
                visit.getPlace().getGooglePlaceId(),
                new PhotoMissionGenerateRequest.DisplayName(
                        visit.getPlaceNameSnapshot(),
                        "ko"
                ),
                List.of("Day " + visit.getDay().getDayNumber()),
                visit.getMatchedPreferences()
        );
    }

    private void ensureUniquePlaces(List<ScheduleVisit> visits) {
        long uniquePlaceCount = visits.stream()
                .map(visit -> visit.getPlace().getGooglePlaceId())
                .distinct()
                .count();
        if (uniquePlaceCount != visits.size()) {
            throw new IllegalStateException(
                    "같은 장소가 일정에 중복되어 있습니다"
            );
        }
    }

    private Map<String, PhotoMissionGenerateResponse.Mission>
    validateAndIndex(
            PhotoMissionGenerateResponse response,
            List<ScheduleVisit> visits
    ) {
        if (response == null || response.missions() == null
                || response.missions().size() != visits.size()) {
            throw new IllegalArgumentException(
                    "AI 포토미션 생성 결과가 일정 장소와 일치하지 않습니다"
            );
        }
        Map<String, PhotoMissionGenerateResponse.Mission> indexed;
        try {
            indexed = response.missions().stream().collect(Collectors.toMap(
                    PhotoMissionGenerateResponse.Mission::placeId,
                    Function.identity()
            ));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "AI 포토미션 생성 결과의 장소가 중복되었습니다",
                    exception
            );
        }
        boolean coversEveryVisit = visits.stream().allMatch(visit ->
                indexed.containsKey(visit.getPlace().getGooglePlaceId())
        );
        if (!coversEveryVisit) {
            throw new IllegalArgumentException(
                    "AI 포토미션 생성 결과가 일정 장소와 일치하지 않습니다"
            );
        }
        return indexed;
    }

    private void createState(
            Mission mission,
            List<TripMember> members,
            LocalDateTime now
    ) {
        if (mission.isGroup()) {
            groupStateRepository.save(MissionGroupState.create(mission, now));
            return;
        }
        participationRepository.saveAll(members.stream()
                .map(member -> MissionParticipation.create(
                        mission,
                        member,
                        now
                ))
                .toList());
    }

    private void requireRunning(MissionGenerationJob job) {
        if (job.getStatus() != MissionGenerationStatus.RUNNING) {
            throw new IllegalStateException(
                    "진행 중인 포토미션 생성 작업이 아닙니다"
            );
        }
    }
}
