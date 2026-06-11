package com.escapii.service;

import com.escapii.dto.CountryDto;
import com.escapii.model.Destination;

import java.util.List;

public interface DestinationService {

    /** Sve aktivne destinacije, opciono filtrirane po aerodromu polaska. */
    List<Destination> getActiveDestinations(String airport);

    /** Sve destinacije (uključuje i neaktivne) - za carousel. */
    List<Destination> getAllDestinations();

    List<CountryDto> fetchCountries();
}
