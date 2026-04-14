package com.escapii.dto;

import com.escapii.model.BookingStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * DTO koji se vraća korisniku nakon uspešne rezervacije.
 */
@Getter
@Builder
public class BookingResponse {
    private String bookingRef;
    private BookingStatus status;
    private Integer totalPriceAll;
    private Integer numberOfTravelers;
}
