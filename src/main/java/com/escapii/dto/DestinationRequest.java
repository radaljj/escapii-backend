package com.escapii.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record DestinationRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 10)  String airportCode,
    @NotBlank @Size(max = 100) String country,
    @Size(max = 50)            String region,
    @NotEmpty                  Set<String> departureAirports
) {}
