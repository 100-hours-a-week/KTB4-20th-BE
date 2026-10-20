package com.planit.photomission.ai;

import com.planit.photomission.domain.Mission;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.ScheduleVisit;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PhotoMissionVerifyRequestFactory {

    public PhotoMissionVerifyRequest create(
            MissionPhoto photo,
            String imageUrl
    ) {
        Mission mission = photo.getMission();
        ScheduleVisit visit = mission.getScheduleVisit();
        Place place = visit.getPlace();

        return new PhotoMissionVerifyRequest(
                imageUrl,
                place.getGooglePlaceId(),
                visit.getPlaceNameSnapshot(),
                coordinates(place.getLatitude(), place.getLongitude()),
                mission.getDescription(),
                coordinatesOrNull(
                        photo.getPhotoLatitude(),
                        photo.getPhotoLongitude()
                )
        );
    }

    private PhotoMissionVerifyRequest.Coordinates coordinates(
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        return new PhotoMissionVerifyRequest.Coordinates(
                latitude,
                longitude
        );
    }

    private PhotoMissionVerifyRequest.Coordinates coordinatesOrNull(
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        if (latitude == null || longitude == null) {
            return null;
        }
        return coordinates(latitude, longitude);
    }
}
