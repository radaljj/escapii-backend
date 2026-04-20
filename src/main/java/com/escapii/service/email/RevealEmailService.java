package com.escapii.service.email;

import com.escapii.model.Booking;

public interface RevealEmailService {

    /** Šalje korisniku reveal email sa magic linkom. */
    void sendRevealEmail(Booking booking);

    /** Šalje timu internu notifikaciju da je reveal poslan. */
    void sendRevealTeamNotification(Booking booking);
}
