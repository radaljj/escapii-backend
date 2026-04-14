package com.escapii.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/**
 * DTO za Destination. active polje se koristi u admin panelu;
 * javni endpoint vraća samo aktivne destinacije pa je uvek true tamo.
 */
@Getter
@Builder
public class DestinationResponse {
    private Long id;
    private String name;
    private String country;
    private String airportCode;
    private String imageUrl;
    private Set<String> departureAirports;
    private Boolean active;
}
