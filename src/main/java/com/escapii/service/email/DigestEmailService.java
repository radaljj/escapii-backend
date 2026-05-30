package com.escapii.service.email;

import com.escapii.model.Booking;

import java.time.LocalDate;
import java.util.List;

public interface DigestEmailService {

    /**
     * @param today            današnji datum
     * @param revealsSent      booking-ovi kojima je reveal email poslan danas
     * @param forecastDue      booking-ovi kojima je prognoza danas
     * @param upcoming         sve CONFIRMED rezervacije u narednih 14 dana
     * @param revealBoxPending booking-ovi sa Reveal Box-om koji još nisu poslati (polazak ≤ 5 dana)
     */
    void sendDailyDigest(LocalDate today,
                         List<Booking> revealsSent,
                         List<Booking> forecastDue,
                         List<Booking> upcoming,
                         List<Booking> revealBoxPending);
}
