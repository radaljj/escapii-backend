package com.escapii.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * Javni DTO za AvailableDate.
 * potentialDestinations su sada uključene — frontend ih koristi za grid isključivanja.
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
    private List<DestinationResponse> potentialDestinations;
}
