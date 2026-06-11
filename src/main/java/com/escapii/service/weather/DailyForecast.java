package com.escapii.service.weather;

import java.time.LocalDate;

/**
 * Prognoza za jedan dan - bez ikakvog podatka o destinaciji.
 */
public record DailyForecast(
        LocalDate date,
        int weatherCode,
        int maxTemp,
        int minTemp,
        double precipitation
) {
    public String emoji() {
        return switch (weatherCode) {
            case 0                           -> "☀️";
            case 1                           -> "🌤";
            case 2                           -> "⛅";
            case 3                           -> "☁️";
            case 45, 48                      -> "🌁";
            case 51, 53, 55                  -> "🌦️";
            case 61, 63, 65, 80, 81, 82      -> "🌧️";
            case 71, 73, 75, 77, 85, 86      -> "🌨️";
            case 95, 96, 99                  -> "⛈️";
            default                          -> "🌡️";
        };
    }

    public String description() {
        return switch (weatherCode) {
            case 0                      -> "Vedro";
            case 1                      -> "Pretežno vedro";
            case 2                      -> "Delimično oblačno";
            case 3                      -> "Oblačno";
            case 45, 48                 -> "Magla";
            case 51, 53, 55             -> "Blaga Kiša";
            case 61, 63, 65             -> "Kiša";
            case 71, 73, 75             -> "Sneg";
            case 77                     -> "Snežne pahulje";
            case 80, 81, 82             -> "Pljuskovi";
            case 85, 86                 -> "Snežni pljuskovi";
            case 95                     -> "Grmljavina";
            case 96, 99                 -> "Grmljavina s gradom";
            default                     -> "Promenljivo";
        };
    }
}
