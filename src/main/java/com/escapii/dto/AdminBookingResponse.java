package com.escapii.dto;

import com.escapii.model.AccommodationType;
import com.escapii.model.BookingStatus;
import com.escapii.dto.TermDestinationResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Admin DTO za pregled rezervacija - sadrži sve relevantne informacije.
 */
@Getter
@Setter
@Builder
public class AdminBookingResponse {

    private Long id;
    private String bookingRef;
    private BookingStatus status;
    private BookingStatus oldStatus;
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

    // Putnici - puni detalji
    private java.util.List<PassengerDetail> passengers;

    // Admin (interno)
    private String adminNotes;

    // Otkrivanje destinacije
    private String assignedDestination;
    private String weatherCity;
    private String airlineName;
    private String airlineBookingCode;
    private LocalDateTime revealSentAt;
    private LocalDateTime forecastSentAt;
    private LocalDateTime destinationRevealedAt;

    // Cene
    private Integer basePricePerPerson;
    private Integer totalPricePerPerson;
    private Integer totalPriceAll;

    // Termin i isključene destinacije (IDs za dropdown)
    private Long selectedDateId;
    private java.util.List<Long> excludedDestinationIds;
    private java.util.List<TermDestinationResponse> termDestinations;

    // Vaučer
    private String  appliedVoucherCode;
    private Integer voucherDiscount;

    // Reveal Box
    private Boolean hasRevealBox;
    private String  deliveryAddress;
    private String  deliveryApartment;
    private String  deliveryCity;
    private String  deliveryPhone;
    private Boolean revealBoxSent;
}
