package com.escapii.event;

import com.escapii.model.Booking;

public class BookingEmailEvent {

    public enum Type {
        TEAM_NOTIFICATION,
        CUSTOMER_CONFIRMATION,
        BOOKING_CONFIRMED,
        BOOKING_CANCELLED
    }

    private final Booking booking;
    private final Type    type;

    public BookingEmailEvent(Booking booking, Type type) {
        this.booking = booking;
        this.type    = type;
    }

    public Booking getBooking() { return booking; }
    public Type    getType()    { return type; }
}
