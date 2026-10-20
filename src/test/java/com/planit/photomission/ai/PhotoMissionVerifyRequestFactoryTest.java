package com.planit.photomission.ai;

import com.planit.photomission.domain.Mission;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.ScheduleVisit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhotoMissionVerifyRequestFactoryTest {

    private final PhotoMissionVerifyRequestFactory factory =
            new PhotoMissionVerifyRequestFactory();

    @DisplayName("미션과 사진 정보로 AI 사진 판정 요청을 만든다")
    @Test
    void createsVerifyRequest() {
        MissionPhoto photo = photo(
                new BigDecimal("35.8350000"),
                new BigDecimal("129.2188000")
        );

        PhotoMissionVerifyRequest request = factory.create(
                photo,
                "https://bucket.s3.ap-northeast-2.amazonaws.com/photo.jpg"
        );

        assertThat(request.imageUrl()).isEqualTo(
                "https://bucket.s3.ap-northeast-2.amazonaws.com/photo.jpg"
        );
        assertThat(request.placeId()).isEqualTo("place_abc");
        assertThat(request.placeName()).isEqualTo("첨성대");
        assertThat(request.placeCoordinates().latitude())
                .isEqualByComparingTo("35.8347000");
        assertThat(request.placeCoordinates().longitude())
                .isEqualByComparingTo("129.2190000");
        assertThat(request.missionDescription())
                .isEqualTo("첨성대 돌 가까이 찍기");
        assertThat(request.photoCoordinates().latitude())
                .isEqualByComparingTo("35.8350000");
        assertThat(request.photoCoordinates().longitude())
                .isEqualByComparingTo("129.2188000");
    }

    @DisplayName("사진 좌표가 완전하지 않으면 AI 요청의 사진 좌표를 null로 만든다")
    @Test
    void createsRequestWithoutPhotoCoordinates() {
        PhotoMissionVerifyRequest request = factory.create(
                photo(new BigDecimal("35.8350000"), null),
                "https://bucket.s3.ap-northeast-2.amazonaws.com/photo.jpg"
        );

        assertThat(request.photoCoordinates()).isNull();
    }

    private MissionPhoto photo(
            BigDecimal photoLatitude,
            BigDecimal photoLongitude
    ) {
        Place place = mock(Place.class);
        when(place.getGooglePlaceId()).thenReturn("place_abc");
        when(place.getLatitude())
                .thenReturn(new BigDecimal("35.8347000"));
        when(place.getLongitude())
                .thenReturn(new BigDecimal("129.2190000"));

        ScheduleVisit visit = mock(ScheduleVisit.class);
        when(visit.getPlace()).thenReturn(place);
        when(visit.getPlaceNameSnapshot()).thenReturn("첨성대");

        Mission mission = mock(Mission.class);
        when(mission.getScheduleVisit()).thenReturn(visit);
        when(mission.getDescription())
                .thenReturn("첨성대 돌 가까이 찍기");

        MissionPhoto photo = mock(MissionPhoto.class);
        when(photo.getMission()).thenReturn(mission);
        when(photo.getPhotoLatitude()).thenReturn(photoLatitude);
        when(photo.getPhotoLongitude()).thenReturn(photoLongitude);
        return photo;
    }
}
