package com.escapii.service.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prazna vrednost u odgovoru servisa se čita kao nula. Bez zaštite bi dan kome fali temperatura
 * postao „Vedro, 0°/0°", pa bi savet za pakovanje usred leta tražio kapu i rukavice.
 */
class OpenMeteoParseTest {

    private static List<DailyForecast> parsiraj(String daily) throws Exception {
        JsonNode cvor = new ObjectMapper().readTree(daily);
        return WeatherServiceImpl.parseOpenMeteo(cvor);
    }

    @Test
    void potpunOdgovor() throws Exception {
        List<DailyForecast> f = parsiraj("""
                {"time":["2026-10-05","2026-10-06"],
                 "weathercode":[0,61],
                 "temperature_2m_max":[25.6,21.4],
                 "temperature_2m_min":[14.5,12.49],
                 "precipitation_sum":[0.0,4.2]}""");
        assertEquals(2, f.size());
        assertEquals(new DailyForecast(LocalDate.of(2026, 10, 5), 0, 26, 15, 0.0), f.get(0));
        assertEquals(new DailyForecast(LocalDate.of(2026, 10, 6), 61, 21, 12, 4.2), f.get(1));
    }

    @Test
    void danBezTemperatureIliKodaSeIzostavlja() throws Exception {
        List<DailyForecast> f = parsiraj("""
                {"time":["2026-10-05","2026-10-06","2026-10-07","2026-10-08"],
                 "weathercode":[0,3,null,2],
                 "temperature_2m_max":[28.0,null,27.0,26.0],
                 "temperature_2m_min":[18.0,17.0,16.0,15.0],
                 "precipitation_sum":[0.0,0.0,0.0,null]}""");
        assertEquals(List.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8)),
                f.stream().map(DailyForecast::date).toList(), "dani bez temperature ili koda ne ulaze u prognozu");
        assertEquals(0.0, f.get(1).precipitation(), "prazne padavine su 0 mm");
        assertTrue(f.stream().allMatch(d -> d.maxTemp() >= 26), "nijedan dan nije postao 0 stepeni");
    }

    @Test
    void kraciNizoviNePucaju() throws Exception {
        List<DailyForecast> f = parsiraj("""
                {"time":["2026-10-05","2026-10-06"],
                 "weathercode":[0],
                 "temperature_2m_max":[25.0,24.0],
                 "temperature_2m_min":[14.0,13.0]}""");
        assertEquals(1, f.size());
        assertEquals(0.0, f.get(0).precipitation());
        assertTrue(parsiraj("{}").isEmpty());
    }
}
