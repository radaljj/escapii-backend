package com.escapii.service;

import com.escapii.model.AvailableDate;

import java.util.List;

public interface AvailableDateService {

    /** Aktivni termini za dati aerodrom polaska, sortirani po datumu. */
    List<AvailableDate> getActiveDatesByAirport(String airport);
}
