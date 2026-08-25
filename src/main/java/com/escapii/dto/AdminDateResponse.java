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
    private final Integer                    agencyCostPrice;
    private final Boolean                    active;
    private final Boolean                    isPrivate;
    private final String                     privateToken;
    private final LocalDateTime              expiresAt;
    private final List<TermDestinationResponse> destinations;
    /** Kome ide privatni link - null za javne termine. */
    private final String                     clientEmail;
    /** Agencija koja organizuje termin - null ako nije dodeljena. */
    private final AgencyResponse             agency;

    public AdminDateResponse(AvailableDate d) {
        this.id              = d.getId();
        this.departureDate   = d.getDepartureDate();
        this.returnDate      = d.getReturnDate();
        this.numberOfNights  = d.getNumberOfNights();
        this.departureAirport = d.getDepartureAirport();
        this.availableSlots  = d.getAvailableSlots();
        this.basePrice       = d.getBasePrice();
        this.agencyCostPrice = d.getAgencyCostPrice();
        this.active          = d.getActive();
        this.isPrivate       = d.getIsPrivate();
        this.privateToken    = d.getPrivateToken();
        this.expiresAt       = d.getExpiresAt();
        this.destinations    = d.getTermDestinations().stream()
                .map(TermDestinationResponse::new)
                .toList();
        this.clientEmail     = d.getClientEmail();
        this.agency          = d.getAgency() == null ? null : AgencyResponse.builder()
                .id(d.getAgency().getId())
                .name(d.getAgency().getName())
                .contactName(d.getAgency().getContactName())
                .contactEmail(d.getAgency().getContactEmail())
                .contactPhone(d.getAgency().getContactPhone())
                .notes(d.getAgency().getNotes())
                .active(d.getAgency().getActive())
                .build();
    }
}
