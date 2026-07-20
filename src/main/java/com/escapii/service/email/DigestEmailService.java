package com.escapii.service.email;

import com.escapii.model.Booking;
import com.escapii.model.LaunchSubscriber;

import java.time.LocalDate;
import java.util.List;

public interface DigestEmailService {

    /**
     * @param today              današnji datum
     * @param revealsSent        booking-ovi kojima je reveal email poslan danas
     * @param forecastDue        booking-ovi kojima je prognoza danas
     * @param upcoming           sve CONFIRMED rezervacije u narednih 14 dana
     * @param revealBoxPending   booking-ovi sa Reveal Box-om koji još nisu poslati (polazak ≤ 5 dana)
     * @param revealedAndViewed  booking-ovi kojima je korisnik otvorio reveal stranicu — tim treba da pošalje potvrdu leta/smeštaja
     * @param notViewedUrgent    booking-ovi kojima je reveal poslan ali korisnik nije otvorio stranicu, polazak ≤ 2 dana
     * @param newLaunchSubscribers nove prijave na "obavesti me kad krenemo live" formu od jučer - privremeno
     */
    void sendDailyDigest(LocalDate today,
                         List<Booking> revealsSent,
                         List<Booking> forecastDue,
                         List<Booking> upcoming,
                         List<Booking> revealBoxPending,
                         List<Booking> revealedAndViewed,
                         List<Booking> notViewedUrgent,
                         List<LaunchSubscriber> newLaunchSubscribers);
}
