package com.escapii.dto;

import com.escapii.model.BookingStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * Javni DTO za pregled statusa rezervacije od strane korisnika.
 * Vraća samo javno dostupne informacije — bez cena, pasoša i internih detalja.
 * passengerNames je sigurno prikazati: ref je random 8-hex UUID fragment, nije pogodiv.
 */
@Getter
@Builder
public class BookingStatusResponse {
    private String        bookingRef;
    private BookingStatus status;
    private String        firstName;
    private String        lastName;
    private String        departureAirport;
    private LocalDate     departureDate;
    private LocalDate     returnDate;
    private Integer       numberOfTravelers;
    private List<String>  passengerNames;
}
