package com.escapii.service.email;

import com.escapii.model.Booking;
import java.time.LocalDate;
import java.util.List;

public interface DigestEmailService {
    void sendDailyDigest(LocalDate today, List<Booking> bookings);
}
