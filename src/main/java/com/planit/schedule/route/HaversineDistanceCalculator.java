package com.planit.schedule.route;

import java.math.BigDecimal;

public final class HaversineDistanceCalculator {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private HaversineDistanceCalculator() {
    }

    public static long distanceMeters(
            BigDecimal fromLatitude,
            BigDecimal fromLongitude,
            BigDecimal toLatitude,
            BigDecimal toLongitude
    ) {
        return distanceMeters(
                fromLatitude.doubleValue(),
                fromLongitude.doubleValue(),
                toLatitude.doubleValue(),
                toLongitude.doubleValue()
        );
    }

    public static long distanceMeters(
            double fromLatitude,
            double fromLongitude,
            double toLatitude,
            double toLongitude
    ) {
        double fromLatitudeRadians = Math.toRadians(fromLatitude);
        double toLatitudeRadians = Math.toRadians(toLatitude);
        double latitudeDelta = toLatitudeRadians - fromLatitudeRadians;
        double longitudeDelta = Math.toRadians(
                toLongitude - fromLongitude
        );

        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(fromLatitudeRadians)
                * Math.cos(toLatitudeRadians)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        double boundedHaversine = Math.min(1.0, Math.max(0.0, haversine));
        double centralAngle = 2 * Math.atan2(
                Math.sqrt(boundedHaversine),
                Math.sqrt(1 - boundedHaversine)
        );
        return Math.round(EARTH_RADIUS_METERS * centralAngle);
    }
}
