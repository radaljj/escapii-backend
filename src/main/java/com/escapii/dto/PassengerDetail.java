package com.escapii.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Detalji jednog putnika - koristi se u AdminBookingResponse.
 * Sadrži sve podatke koje putnik unosi pri rezervaciji.
 */
@Getter
@Builder
public class PassengerDetail {

    private String    name;
    private String    gender;          // "M" ili "F"
    private LocalDate dateOfBirth;
    private String    passportCountry; // može biti null (zemlja pasoša)
    private String    passportNumber;  // može biti null (serijski broj)
    private Boolean   hasValidPassport;
    private String    visaInfo;        // može biti null
}
