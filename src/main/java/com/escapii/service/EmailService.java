package com.escapii.service;

import com.escapii.model.Booking;

public interface EmailService {

    /**
     * Salje email timu Escapii sa kompletnim detaljima novog upita.
     * Implementacija je async — ne blokira HTTP response.
     */
    void sendTeamNotification(Booking booking);

    /**
     * Salje potvrdu korisniku odmah nakon primljenog upita.
     * Implementacija je async — ne blokira HTTP response.
     */
    void sendCustomerConfirmation(Booking booking);

    /**
     * Salje email korisniku kada admin potvrdi (CONFIRMED) rezervaciju.
     */
    void sendBookingConfirmed(Booking booking);

    /**
     * Salje email korisniku kada admin otkaže (CANCELLED) rezervaciju koja je bila CONFIRMED.
     */
    void sendBookingCancelled(Booking booking);

    /** Jutarnji operativni digest — šalje se na ops-email svako jutro u 08:00. */
    void sendDailyDigest(java.time.LocalDate today, java.util.List<com.escapii.model.Booking> bookings);

    /** Potvrda korisniku da je dodat na listu čekanja. */
    void sendWaitlistConfirmation(String email, String airport);

    /** Notifikacija svim čekajućima da su se otvorili novi termini za dati aerodrom. */
    void sendWaitlistNotification(String email, String airport);
}
