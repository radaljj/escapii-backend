package com.escapii.service.email;

import com.escapii.model.Booking;

import java.time.LocalDate;
import java.util.List;

public interface DigestEmailService {

    void sendDailyDigest(LocalDate today,
                         List<Booking> revealsSent,
                         List<Booking> forecastDue,
                         List<Booking> upcoming,
                         List<Booking> revealBoxPending,
                         List<Booking> revealedAndViewed,
                         List<Booking> notViewedUrgent);
}
