package com.escapii.dto;

import com.escapii.model.DepartureAirport;

/**
 * Jedan aerodrom polaska za frontend (javni sajt i admin panel).
 * Sve vrednosti dolaze iz {@link DepartureAirport} - jedine definicije u sistemu.
 */
public record DepartureAirportResponse(
        String  code,
        String  citySr,
        String  cityEn,
        String  airportName,
        String  airportNameEn,
        String  imageSlug,
        int     maxExclusions,
        boolean firstExclusionFree,
        boolean publiclySelectable,
        boolean domestic
) {
    public static DepartureAirportResponse of(DepartureAirport a) {
        return new DepartureAirportResponse(
                a.code(), a.citySr(), a.cityEn(), a.airportName(), a.airportNameEn(),
                a.imageSlug(), a.maxExclusions(), a.firstExclusionFree(),
                a.publiclySelectable(), a.domestic());
    }
}
