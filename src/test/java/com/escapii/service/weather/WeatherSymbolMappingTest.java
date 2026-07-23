package com.escapii.service.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MET Norway (rezervni izvor) koristi tekstualne oznake vremena, a mejl
 * očekuje WMO numerički kod za emoji. Ako mapiranje pukne, prognoza iz
 * rezervnog izvora bi imala pogrešan emoji - test čuva ključne slučajeve.
 */
class WeatherSymbolMappingTest {

    @Test
    void osnovneOznakeSeMapirajuTacno() {
        assertEquals(0,  WeatherServiceImpl.symbolToWmoCode("clearsky_day"),   "vedro");
        assertEquals(1,  WeatherServiceImpl.symbolToWmoCode("fair_day"),       "pretežno vedro");
        assertEquals(2,  WeatherServiceImpl.symbolToWmoCode("partlycloudy_night"), "delimično oblačno");
        assertEquals(3,  WeatherServiceImpl.symbolToWmoCode("cloudy"),         "oblačno");
        assertEquals(61, WeatherServiceImpl.symbolToWmoCode("rain"),           "kiša");
        assertEquals(65, WeatherServiceImpl.symbolToWmoCode("heavyrain"),      "jaka kiša");
        assertEquals(51, WeatherServiceImpl.symbolToWmoCode("lightrain"),      "blaga kiša");
        assertEquals(71, WeatherServiceImpl.symbolToWmoCode("snow"),           "sneg");
        assertEquals(95, WeatherServiceImpl.symbolToWmoCode("rainandthunder"), "grmljavina");
        assertEquals(45, WeatherServiceImpl.symbolToWmoCode("fog"),            "magla");
    }

    @Test
    void svakiWmoKodDajeValidanEmoji() {
        // Bilo koji kod koji mapiranje vrati mora da ima definisan emoji (ne prazan).
        for (String symbol : new String[]{
                "clearsky_day", "fair_night", "partlycloudy_day", "cloudy",
                "lightrain", "rain", "heavyrain", "rainshowers_day", "snow",
                "sleet", "fog", "rainandthunder", "unknown_symbol", ""}) {
            int code = WeatherServiceImpl.symbolToWmoCode(symbol);
            String emoji = new DailyForecast(java.time.LocalDate.now(), code, 20, 10, 0).emoji();
            assertNotNull(emoji);
            assertFalse(emoji.isBlank(), "kod " + code + " (iz '" + symbol + "') nema emoji");
        }
    }

    /** Nepoznata ili prazna oznaka ne sme da baci - vraća bezbedan podrazumevani kod. */
    @Test
    void nepoznataOznakaNeBaca() {
        assertEquals(3, WeatherServiceImpl.symbolToWmoCode(null));
        assertEquals(3, WeatherServiceImpl.symbolToWmoCode(""));
        assertEquals(3, WeatherServiceImpl.symbolToWmoCode("nekakva_buducа_oznaka_2050"));
    }
}
