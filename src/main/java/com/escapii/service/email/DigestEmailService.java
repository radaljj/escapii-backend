package com.escapii.service.email;

import com.escapii.model.Booking;

import java.time.LocalDate;
import java.util.List;

public interface DigestEmailService {

    /**
     * @param today       današnji datum
     * @param revealsSent booking-ovi kojima je reveal email poslan danas
     * @param forecastDue booking-ovi kojima je prognoza danas (T+5) — za sada informativno
     * @param upcoming    sve CONFIRMED rezervacije u narednih 14 dana (za preview sekciju)
     */
    void sendDailyDigest(LocalDate today,
                         List<Booking> revealsSent,
                         List<Booking> forecastDue,
                         List<Booking> upcoming);
}
