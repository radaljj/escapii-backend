package com.escapii.dto;

import com.escapii.model.TermDestination;
import lombok.Getter;

@Getter
public class TermDestinationResponse {
    private final Long   id;
    private final Long   destinationId;
    private final String name;
    private final String nameEn;
    private final String airportCode;
    private final String country;
    private final String countryEn;
    private final String imageUrl;
    private final boolean active;

    public TermDestinationResponse(TermDestination td) {
        this.id            = td.getId();
        this.destinationId = td.getDestination().getId();
        this.name          = td.getDestination().getName();
        this.nameEn        = td.getDestination().getNameEn();
        this.airportCode   = td.getDestination().getAirportCode();
        this.country       = td.getDestination().getCountry();
        this.countryEn     = td.getDestination().getCountryEn();
        this.imageUrl      = td.getDestination().getImageUrl();
        this.active        = td.isActive();
    }
}
