package com.escapii.service;

import com.escapii.dto.CountryDto;
import com.escapii.model.Destination;

import java.util.List;

public interface DestinationService {

    /** Sve destinacije filtrirane po aerodromu (za carousel i stari fallback). */
    List<Destination> getDestinationsByAirport(String airport);

    /** Sve destinacije sortirane po imenu - za carousel. */
    List<Destination> getAllDestinations();

    List<CountryDto> fetchCountries();
}
