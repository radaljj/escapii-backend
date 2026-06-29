package com.escapii.dto;

import com.escapii.model.AvailableDate;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class AdminDateResponse {

    private final Long                       id;
    private final LocalDate                  departureDate;
    private final LocalDate                  returnDate;
    private final Integer                    numberOfNights;
    private final String                     departureAirport;
    private final Integer                    availableSlots;
    private final Integer                    basePrice;
    private final Boolean                    active;
    private final Boolean                    isPrivate;
    private final String                     privateToken;
    private final LocalDateTime              expiresAt;
    private final List<TermDestinationResponse> destinations;

    public AdminDateResponse(AvailableDate d) {
        this.id              = d.getId();
        this.departureDate   = d.getDepartureDate();
        this.returnDate      = d.getReturnDate();
        this.numberOfNights  = d.getNumberOfNights();
        this.departureAirport = d.getDepartureAirport();
        this.availableSlots  = d.getAvailableSlots();
        this.basePrice       = d.getBasePrice();
        this.active          = d.getActive();
        this.isPrivate       = d.getIsPrivate();
        this.privateToken    = d.getPrivateToken();
        this.expiresAt       = d.getExpiresAt();
        this.destinations    = d.getTermDestinations().stream()
                .map(TermDestinationResponse::new)
                .toList();
    }
}
