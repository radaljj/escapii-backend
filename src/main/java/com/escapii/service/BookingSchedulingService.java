package com.escapii.service;

import java.util.Map;

public interface BookingSchedulingService {
    void sendPendingReveals();
    void sendPendingForecasts();
    void cancelStalePendingBookings();
    Map<String, String> sendRevealForBooking(Long bookingId, String siteUrl);
    Map<String, String> sendForecastForBooking(Long bookingId);
}
