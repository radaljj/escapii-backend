package com.escapii.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Javni DTO za AvailableDate. Ne sadrži potentialDestinations — to je admin-only.
 */
@Getter
@Builder
public class DateResponse {
    private Long id;
    private String departureAirport;
    private LocalDate departureDate;
    private LocalDate returnDate;
    private Integer numberOfNights;
    private Integer availableSlots;
    private Integer basePrice;
}
