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
}
