package com.escapii.service.weather;

import java.util.List;
import java.util.Optional;

public interface WeatherService {
    /**
     * Geocodira naziv grada pa preuzima 7-dnevnu prognozu.
     * Vraća empty ako grad nije pronađen ili API ne odgovori.
     *
     * @param cityName naziv destinacije (npr. "Rome", "Rim", "Barselona")
     */
    Optional<List<DailyForecast>> getForecast(String cityName);

    /**
     * Unapred geokodira upit i zapamti koordinate (keš), da kasnije preuzimanje prognoze
     * ne zavisi od geokodera. Sme da traje (mrežni pozivi) - zvati iz pozadine.
     */
    default void warmUp(String cityName) {}
}
