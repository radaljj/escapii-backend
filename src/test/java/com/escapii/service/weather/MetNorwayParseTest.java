package com.escapii.service.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rezervni izvor (MET Norway) daje satni blok samo za prvih oko dva i po dana, a dalje tačke na
 * šest sati. Dok je kod čitao samo satni blok, svaki dalji dan je izlazio kao „Oblačno" bez
 * padavina - baš dani puta kad mejl ide sedam dana unapred. Oblik odgovora je prepisan iz
 * stvarnog odgovora servisa od 28.09.2026.
 */
class MetNorwayParseTest {

    private static String tacka(String vreme, double temp, String blokovi) {
        return "{\"time\":\"" + vreme + "\",\"data\":{\"instant\":{\"details\":{\"air_temperature\":" + temp + "}}"
                + (blokovi.isEmpty() ? "" : "," + blokovi) + "}}";
    }

    private static String blok(String ime, String simbol, double mm) {
        return "\"" + ime + "\":{\"summary\":{\"symbol_code\":\"" + simbol + "\"},\"details\":{\"precipitation_amount\":" + mm + "}}";
    }

    private static List<DailyForecast> parsiraj(String... tacke) throws Exception {
        JsonNode series = new ObjectMapper().readTree("[" + String.join(",", tacke) + "]");
        return WeatherServiceImpl.parseMetNorway(series);
    }

    @Test
    void sestocasovniDanDobijaOznakuIPadavine() throws Exception {
        List<DailyForecast> f = parsiraj(
                tacka("2026-10-05T00:00:00Z", 9.0,  blok("next_6_hours", "cloudy", 0.0)),
                tacka("2026-10-05T06:00:00Z", 8.0,  blok("next_6_hours", "lightrain", 1.5)),
                tacka("2026-10-05T12:00:00Z", 14.0, blok("next_6_hours", "rain", 3.0)),
                tacka("2026-10-05T18:00:00Z", 11.0, blok("next_6_hours", "rain", 2.5)));

        assertEquals(1, f.size());
        DailyForecast d = f.get(0);
        assertEquals(LocalDate.of(2026, 10, 5), d.date());
        assertEquals(61, d.weatherCode(), "oznaka iz podnevne tačke (rain), ne podrazumevano oblačno");
        assertEquals("Kiša", d.description());
        assertEquals(14, d.maxTemp());
        assertEquals(8, d.minTemp());
        assertEquals(7.0, d.precipitation(), 0.001, "zbir četiri šestočasovna bloka");
        assertTrue(d.rainy());
    }

    /** Satne tačke nose i next_6_hours - ako bi se čitala oba bloka, iste padavine bi se sabrale dvaput. */
    @Test
    void satniDanNeSabiraPadavineDvaput() throws Exception {
        List<DailyForecast> f = parsiraj(
                tacka("2026-10-01T10:00:00Z", 18.0, blok("next_1_hours", "cloudy", 0.2) + "," + blok("next_6_hours", "rain", 5.0)),
                tacka("2026-10-01T12:00:00Z", 21.0, blok("next_1_hours", "fair_day", 0.0) + "," + blok("next_6_hours", "rain", 5.0)),
                tacka("2026-10-01T13:00:00Z", 20.0, blok("next_1_hours", "fair_day", 0.3) + "," + blok("next_6_hours", "rain", 5.0)));

        DailyForecast d = f.get(0);
        assertEquals(0.5, d.precipitation(), 0.001, "samo satni iznosi: 0,2 + 0 + 0,3");
        assertEquals(1, d.weatherCode(), "oznaka oko podneva iz satnog bloka (fair_day)");
        assertEquals(21, d.maxTemp());
        assertEquals(18, d.minTemp());
    }

    /** Dan prelaza: jutarnje tačke su satne, ostatak dana šestočasovni - sabira se svaka tačka svojim blokom. */
    @Test
    void danPrelazaSaSatnihNaSestocasovne() throws Exception {
        List<DailyForecast> f = parsiraj(
                tacka("2026-10-04T04:00:00Z", 10.0, blok("next_1_hours", "cloudy", 0.1) + "," + blok("next_6_hours", "cloudy", 0.4)),
                tacka("2026-10-04T05:00:00Z", 10.0, blok("next_1_hours", "cloudy", 0.1) + "," + blok("next_6_hours", "cloudy", 0.4)),
                tacka("2026-10-04T06:00:00Z", 11.0, blok("next_6_hours", "lightrain", 1.0)),
                tacka("2026-10-04T12:00:00Z", 15.0, blok("next_6_hours", "heavyrain", 9.0)),
                tacka("2026-10-04T18:00:00Z", 12.0, blok("next_6_hours", "rain", 2.0)));

        DailyForecast d = f.get(0);
        assertEquals(12.2, d.precipitation(), 0.001, "0,1 + 0,1 + 1 + 9 + 2");
        assertEquals(65, d.weatherCode(), "podne: heavyrain");
    }

    /** Poslednji dan u nizu nosi samo ponoćnu tačku bez blokova - takav dan se ne prikazuje kao „Oblačno 10°/10°". */
    @Test
    void danSaJednomTackomSeIzostavlja() throws Exception {
        List<DailyForecast> f = parsiraj(
                tacka("2026-10-07T12:00:00Z", 16.0, blok("next_6_hours", "fair_day", 0.0)),
                tacka("2026-10-07T18:00:00Z", 13.0, blok("next_6_hours", "clearsky_night", 0.0)),
                tacka("2026-10-08T00:00:00Z", 10.0, ""));

        assertEquals(1, f.size(), "dan od jedne tačke ne ulazi u prognozu");
        assertEquals(LocalDate.of(2026, 10, 7), f.get(0).date());
    }

    @Test
    void redosledDanaJeHronoloski() throws Exception {
        List<DailyForecast> f = parsiraj(
                tacka("2026-10-05T06:00:00Z", 8.0,  blok("next_6_hours", "snow", 2.0)),
                tacka("2026-10-05T12:00:00Z", 1.0,  blok("next_6_hours", "snow", 2.0)),
                tacka("2026-10-06T06:00:00Z", 12.0, blok("next_6_hours", "fog", 0.0)),
                tacka("2026-10-06T12:00:00Z", 17.0, blok("next_6_hours", "partlycloudy_day", 0.0)));

        assertEquals(List.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6)), f.stream().map(DailyForecast::date).toList());
        assertEquals("Sneg", f.get(0).description());
        assertEquals("Delimično oblačno", f.get(1).description());
    }
}
