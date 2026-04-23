package com.escapii.service.email;

import com.escapii.model.Booking;

public interface RevealEmailService {

    /** Šalje korisniku reveal email — koristi konfigurisani frontend URL. */
    void sendRevealEmail(Booking booking);

    /**
     * Šalje korisniku reveal email sa eksplicitnim siteUrl.
     * Koristi se pri ručnom slanju iz admin panela — frontend šalje svoj URL
     * tako da link u emailu uvek vodi na aktuelni sajt (test/production/rs/com).
     */
    void sendRevealEmail(Booking booking, String siteUrl);

    /** Šalje timu internu notifikaciju da je reveal poslan. */
    void sendRevealTeamNotification(Booking booking);
}
