package com.escapii.dto;

import com.escapii.model.CustomDateInquiry;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Admin prikaz jednog custom-date upita.
 * Sadrži sve podatke koje je korisnik uneo.
 */
@Getter
public class CustomDateInquiryResponse {

    private final Long          id;
    private final String        airport;
    private final Integer       travelers;
    private final LocalDate     desiredDepartureDate;
    private final Integer       nights;
    private final String        email;
    private final String        notes;
    private final String        status;
    private final BigDecimal    price;
    private final LocalDateTime createdAt;

    public CustomDateInquiryResponse(CustomDateInquiry e) {
        this.id                   = e.getId();
        this.airport              = e.getAirport();
        this.travelers            = e.getTravelers();
        this.desiredDepartureDate = e.getDesiredDepartureDate();
        this.nights               = e.getNights();
        this.email                = e.getEmail();
        this.notes                = e.getNotes();
        this.status               = e.getStatus().name();
        this.price                = e.getPrice();
        this.createdAt            = e.getCreatedAt();
    }
}
