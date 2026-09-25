package com.planit.schedule.route;

public class RouteCalculationException extends RuntimeException {

    private final Reason reason;

    public RouteCalculationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        INVALID_PLACE_RESULT,
        ROUTE_NOT_FOUND
    }
}
