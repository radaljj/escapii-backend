package com.escapii.service.email;

import com.escapii.model.Booking;

public interface BookingEmailService {
    void sendTeamNotification(Booking booking);
    void sendCustomerConfirmation(Booking booking);
    void sendBookingConfirmed(Booking booking);
    void sendBookingCancelled(Booking booking);
}
