package com.escapii.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO za postavljanje weather city overridea.
 * weatherCity je geocoding hint koji se koristi umesto assignedDestination
 * kada se preuzima vremenska prognoza (npr. "Santa Cruz de Tenerife, Spain" umesto "Tenerife").
 */
public record AdminWeatherCityRequest(
        @Size(max = 200, message = "Naziv ne sme biti duži od 200 karaktera")
        String weatherCity
) {}
