package com.escapii.event;

import com.escapii.model.Booking;
import com.escapii.service.email.BookingEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEmailListener {

    private final BookingEmailService bookingEmailService;

    // Mejl se šalje tek POSLE uspešnog DB commit-a - garantuje da korisnik ne
    // dobije potvrdu za rezervaciju čija je transakcija rollbackovana.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingEmail(BookingEmailEvent event) {
        Booking booking = event.getBooking();
        switch (event.getType()) {
            case TEAM_NOTIFICATION     -> bookingEmailService.sendTeamNotification(booking);
            case CUSTOMER_CONFIRMATION -> bookingEmailService.sendCustomerConfirmation(booking);
            case BOOKING_CONFIRMED     -> bookingEmailService.sendBookingConfirmed(booking);
            case BOOKING_CANCELLED     -> bookingEmailService.sendBookingCancelled(booking);
        }
    }
}
