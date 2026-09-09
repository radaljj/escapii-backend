package com.escapii.service.email;

import com.escapii.model.Booking;

public interface BookingEmailService {
    void sendTeamNotification(Booking booking);
    void sendCustomerConfirmation(Booking booking);
    void sendBookingConfirmed(Booking booking);

    /**
     * Isto što i {@link #sendBookingConfirmed}, ali sinhrono i sa ishodom - za admin
     * "pošalji ponovo". {@code giftVoucherPdf} ide u prilog kad nije null (poklon).
     */
    boolean sendBookingConfirmedNow(Booking booking, byte[] giftVoucherPdf);
    void sendBookingCancelled(Booking booking);
}
