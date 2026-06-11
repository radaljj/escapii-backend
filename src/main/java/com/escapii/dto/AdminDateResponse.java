package com.escapii.dto;

import com.escapii.model.AvailableDate;
import com.escapii.model.Destination;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response za admin prikaz termina - uključuje potencijalne destinacije.
 * Nikad se ne šalje korisnicima.
 */
@Getter
public class AdminDateResponse {

    private final Long                  id;
    private final LocalDate             departureDate;
    private final LocalDate             returnDate;
    private final Integer               numberOfNights;
    private final String                departureAirport;
    private final Integer               availableSlots;
    private final Integer               basePrice;
    private final Boolean               active;
    private final Boolean               isPrivate;
    private final String                privateToken;
    private final LocalDateTime         expiresAt;
    private final List<DestinationSummary> potentialDestinations;

    public AdminDateResponse(AvailableDate d) {
        this.id                   = d.getId();
        this.departureDate        = d.getDepartureDate();
        this.returnDate           = d.getReturnDate();
        this.numberOfNights       = d.getNumberOfNights();
        this.departureAirport     = d.getDepartureAirport();
        this.availableSlots       = d.getAvailableSlots();
        this.basePrice            = d.getBasePrice();
        this.active               = d.getActive();
        this.isPrivate            = d.getIsPrivate();
        this.privateToken         = d.getPrivateToken();
        this.expiresAt            = d.getExpiresAt();
        this.potentialDestinations = d.getPotentialDestinations().stream()
                .map(DestinationSummary::new)
                .toList();
    }

    @Getter
    public static class DestinationSummary {
        private final Long id;
        private final String name;
        private final String country;
        private final String airportCode;

        public DestinationSummary(Destination dest) {
            this.id = dest.getId();
            this.name = dest.getName();
            this.country = dest.getCountry();
            this.airportCode = dest.getAirportCode();
        }
    }
}
