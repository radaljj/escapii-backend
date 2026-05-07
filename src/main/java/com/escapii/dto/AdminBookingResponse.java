package com.escapii.dto;

import com.escapii.model.AccommodationType;
import com.escapii.model.BookingStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Admin DTO za pregled rezervacija — sadrži sve relevantne informacije.
 */
@Getter
@Builder
public class AdminBookingResponse {

    private Long id;
    private String bookingRef;
    private BookingStatus status;
    private LocalDateTime createdAt;

    // Kontakt
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String notes;

    // Putovanje
    private String departureAirport;
    private Integer numberOfTravelers;
    private LocalDate departureDate;
    private LocalDate returnDate;
    private AccommodationType accommodationType;

    // Dodaci
    private Boolean hasInsurance;
    private Boolean hasBreakfast;
    private Boolean hasSeatsTogether;
    private Boolean hasConnectingFlights;
    private Integer cabinSuitcaseCount;
    private Integer exclusionCount;
    private java.util.List<String> excludedDestinations;
    private Integer exclusionCostEur;

    // Putnici
    private java.util.List<String> passengerNames;

    // Admin (interno)
    private String adminNotes;

    // Otkrivanje destinacije
    private String assignedDestination;
    private String weatherCity;
    private LocalDateTime revealSentAt;
    private LocalDateTime forecastSentAt;
    private LocalDateTime destinationRevealedAt;

    // Cene
    private Integer basePricePerPerson;
    private Integer totalPricePerPerson;
    private Integer totalPriceAll;
}
