package com.planit.photomission.repository;

import com.planit.domain.ImageFile;
import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.photomission.domain.Mission;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionParticipation;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.domain.MissionScope;
import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.repository.ImageFileRepository;
import com.planit.repository.RegionRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.PlaceDetails;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleVisit;
import com.planit.schedule.repository.PlaceRepository;
import com.planit.schedule.repository.ScheduleDayRepository;
import com.planit.schedule.repository.ScheduleRepository;
import com.planit.schedule.repository.ScheduleVisitRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PhotoMissionRepositoryTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 10, 10, 12, 0);

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private MissionParticipationRepository participationRepository;

    @Autowired
    private MissionGroupStateRepository groupStateRepository;

    @Autowired
    private MissionPhotoRepository photoRepository;

    @Autowired
    private PhotoEvaluationRepository evaluationRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleDayRepository scheduleDayRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private ScheduleVisitRepository visitRepository;

    @Autowired
    private ImageFileRepository imageFileRepository;

    @DisplayName("미션의 개인 참여 상태와 단체 공유 상태를 조회한다")
    @Test
    void findsPersonalAndGroupState() {
        Fixture fixture = createFixture();

        Mission personalMission = missionRepository.save(Mission.create(
                fixture.visit(), 1, MissionScope.PERSONAL,
                "유적지 전경 담기", "역사", NOW
        ));
        MissionParticipation participation = participationRepository.save(
                MissionParticipation.create(
                        personalMission, fixture.member(), NOW
                )
        );
        Mission groupMission = missionRepository.save(Mission.create(
                fixture.visit(), 2, MissionScope.GROUP,
                "다 같이 사진 찍기", null, NOW
        ));
        MissionGroupState groupState = groupStateRepository.save(
                MissionGroupState.create(groupMission, NOW)
        );

        assertThat(participationRepository
                .findByMissionIdAndTripMemberId(
                        personalMission.getId(), fixture.member().getId()
                )).contains(participation);
        assertThat(groupStateRepository.findByMissionId(groupMission.getId()))
                .contains(groupState);
        assertThat(missionRepository
                .findByTripIdAndScheduleDayDayNumberOrderByMissionOrderAsc(
                        fixture.member().getTrip().getId(),
                        (byte) 1
                )).containsExactly(personalMission, groupMission);
        assertThat(participationRepository.findByMissionIdIn(List.of(
                personalMission.getId(),
                groupMission.getId()
        ))).containsExactly(participation);
        assertThat(groupStateRepository.findByMissionIdIn(List.of(
                personalMission.getId(),
                groupMission.getId()
        ))).containsExactly(groupState);
    }

    @DisplayName("멱등성 키와 활성·대표 슬롯으로 사진을 조회한다")
    @Test
    void findsPhotoByIdempotencyAndSlots() {
        Fixture fixture = createFixture();
        Mission personalMission = missionRepository.save(Mission.create(
                fixture.visit(), 1, MissionScope.PERSONAL,
                "유적지 전경 담기", "역사", NOW
        ));
        MissionParticipation participation = participationRepository.save(
                MissionParticipation.create(
                        personalMission, fixture.member(), NOW
                )
        );
        UUID idempotencyKey = UUID.randomUUID();
        MissionPhoto personalPhoto = photoRepository.save(
                MissionPhoto.personal(
                        participation,
                        createImageFile(),
                        idempotencyKey,
                        BigDecimal.valueOf(35.8347),
                        BigDecimal.valueOf(129.2190),
                        NOW
                )
        );

        Mission groupMission = missionRepository.save(Mission.create(
                fixture.visit(), 2, MissionScope.GROUP,
                "다 같이 사진 찍기", null, NOW
        ));
        MissionPhoto groupPhoto = MissionPhoto.group(
                groupMission,
                fixture.member(),
                createImageFile(),
                UUID.randomUUID(),
                null,
                null,
                NOW
        );
        groupPhoto.markRepresentative();
        photoRepository.save(groupPhoto);

        assertThat(photoRepository.findByIdempotencyKey(idempotencyKey))
                .contains(personalPhoto);
        assertThat(photoRepository.findActiveByParticipationId(
                participation.getId()
        )).contains(personalPhoto);
        assertThat(photoRepository.findRepresentativeByMissionId(
                groupMission.getId()
        )).contains(groupPhoto);
        assertThat(photoRepository.findVisibleByMissionIdIn(List.of(
                personalMission.getId(),
                groupMission.getId()
        ))).containsExactlyInAnyOrder(personalPhoto, groupPhoto);
    }

    @DisplayName("사진의 가장 최근 AI 실행 시도를 조회한다")
    @Test
    void findsLatestEvaluationAttempt() {
        Fixture fixture = createFixture();
        Mission mission = missionRepository.save(Mission.create(
                fixture.visit(), 1, MissionScope.PERSONAL,
                "유적지 전경 담기", "역사", NOW
        ));
        MissionParticipation participation = participationRepository.save(
                MissionParticipation.create(mission, fixture.member(), NOW)
        );
        MissionPhoto photo = photoRepository.save(MissionPhoto.personal(
                participation,
                createImageFile(),
                UUID.randomUUID(),
                null,
                null,
                NOW
        ));
        evaluationRepository.save(PhotoEvaluation.start(photo, 1, NOW));
        PhotoEvaluation latest = evaluationRepository.save(
                PhotoEvaluation.start(photo, 2, NOW.plusSeconds(1))
        );

        assertThat(evaluationRepository
                .findTopByMissionPhotoIdOrderByAttemptNoDesc(photo.getId()))
                .contains(latest);
    }

    private Fixture createFixture() {
        Region region = regionRepository.findById(1L).orElseThrow();
        LocalDate tripDate = LocalDate.of(2026, 10, 10);
        Trip trip = tripRepository.save(new Trip(
                region,
                "경주 여행",
                tripDate,
                tripDate.plusDays(2),
                (byte) 4,
                tripDate.minusDays(1).atTime(23, 59, 59)
        ));
        User user = userRepository.save(new User(
                null, UUID.randomUUID(), "포토미션사용자"
        ));
        TripMember member = tripMemberRepository.save(
                TripMember.createMember(trip, user)
        );
        Schedule schedule = scheduleRepository.save(new Schedule(trip, NOW));
        ScheduleDay day = scheduleDayRepository.save(
                new ScheduleDay(schedule, (byte) 1, tripDate)
        );
        Place place = placeRepository.save(new Place(
                region,
                new PlaceDetails(
                        "place_" + UUID.randomUUID(),
                        "첨성대",
                        "관광명소",
                        "경주시 인왕동",
                        null,
                        BigDecimal.valueOf(129.2190),
                        BigDecimal.valueOf(35.8347),
                        null,
                        null
                ),
                NOW
        ));
        ScheduleVisit visit = visitRepository.save(
                new ScheduleVisit(day, place, 1, "역사 취향 반영", NOW)
        );
        return new Fixture(member, visit);
    }

    private ImageFile createImageFile() {
        return imageFileRepository.save(ImageFile.profile(
                "missions/" + UUID.randomUUID() + ".jpg",
                "image/jpeg",
                1024
        ));
    }

    private record Fixture(TripMember member, ScheduleVisit visit) {
    }
}
