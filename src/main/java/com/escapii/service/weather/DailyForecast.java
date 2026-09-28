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
            case 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️";
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
            case 51, 53, 55             -> "Slaba kiša";
            case 56, 57, 66, 67         -> "Ledena kiša";
            case 61, 63, 65             -> "Kiša";
            case 71, 73, 75             -> "Sneg";
            case 77                     -> "Sitan sneg";
            case 80, 81, 82             -> "Pljuskovi";
            case 85, 86                 -> "Snežni pljuskovi";
            case 95                     -> "Grmljavina";
            case 96, 99                 -> "Grmljavina s gradom";
            default                     -> "Promenljivo";
        };
    }

    /**
     * Padavine koje vredi pomenuti: bar 1 mm. Isti prag važi i za prikaz kapljice u mejlu i za
     * brojanje kišnih dana - sa dva praga je dan od 0,7 mm imao kapljicu, a savet nije pominjao kišu.
     */
    public boolean imaPadavina() {
        return precipitation >= 1.0;
    }

    /** Dan sa kišom: bar 1 mm padavina ili kod za kišu, ledenu kišu, pljusak ili grmljavinu. */
    public boolean rainy() {
        return imaPadavina() || switch (weatherCode) {
            case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99 -> true;
            default -> false;
        };
    }

    public boolean snowy() {
        return switch (weatherCode) {
            case 71, 73, 75, 77, 85, 86 -> true;
            default -> false;
        };
    }

    /**
     * Sneg zbog kog se drugačije pakuje: kod za sneg I stvarno hladan dan (najviše 10 stepeni
     * preko dana, noću do 3). Kod za sneg uz topao dan je greška u podacima - savet ga tada
     * ne pominje, da kupcu ne stigne „vrelo je, ima i snega".
     */
    public boolean snegZaPakovanje() {
        return snowy() && maxTemp <= 10 && minTemp <= 3;
    }
}
