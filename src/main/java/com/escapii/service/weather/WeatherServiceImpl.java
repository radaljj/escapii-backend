package com.escapii.service.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class WeatherServiceImpl implements WeatherService {

    // ── Konstante ─────────────────────────────────────────────────────────────

    private static final String NOMINATIM_URL =
            "https://nominatim.openstreetmap.org/search?q=%s&format=json&limit=1&featuretype=city";

    private static final String OPEN_METEO_URL =
            "https://api.open-meteo.com/v1/forecast"
            + "?latitude=%.4f&longitude=%.4f"
            + "&daily=weathercode,temperature_2m_max,temperature_2m_min,precipitation_sum"
            + "&timezone=auto&forecast_days=11";

    private static final String USER_AGENT = "Escapii/1.0 (contact@escapii.com)";
    private static final int    TIMEOUT_SEC = 10;

    // ── Statičke instance (thread-safe) ───────────────────────────────────────

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SEC))
            .build();

    // ── Javni API ─────────────────────────────────────────────────────────────

    @Override
    public Optional<List<DailyForecast>> getForecast(String cityName) {
        try {
            double[] coords = geocode(cityName);
            if (coords == null) {
                log.warn("[Weather] Geocoding nije vratio rezultat za: '{}'", cityName);
                return Optional.empty();
            }
            List<DailyForecast> forecast = fetchForecast(coords[0], coords[1]);
            log.info("[Weather] Prognoza preuzeta za '{}' → lat={}, lon={}, dana={}",
                    cityName, coords[0], coords[1], forecast.size());
            return Optional.of(forecast);
        } catch (Exception e) {
            log.error("[Weather] Greška pri preuzimanju prognoze za '{}': {}", cityName, e.getMessage(), e);
            return Optional.empty();
        }
    }

    // ── Geocoding (Nominatim / OpenStreetMap) ─────────────────────────────────

    /** Vraća [lat, lon] ili null ako grad nije pronađen. */
    private double[] geocode(String cityName) throws Exception {
        String encoded = URLEncoder.encode(cityName, StandardCharsets.UTF_8);
        String url     = NOMINATIM_URL.formatted(encoded);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en")
                .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("[Weather] Nominatim HTTP {}", response.statusCode());
            return null;
        }

        JsonNode results = MAPPER.readTree(response.body());
        if (!results.isArray() || results.isEmpty()) return null;

        JsonNode first = results.get(0);
        return new double[]{ first.get("lat").asDouble(), first.get("lon").asDouble() };
    }

    // ── Vremenska prognoza (Open-Meteo) ───────────────────────────────────────

    /** Preuzima 7-dnevnu dnevnu prognozu. Nikad ne šalje ime grada ka API-ju. */
    private List<DailyForecast> fetchForecast(double lat, double lon) throws Exception {
        String url = OPEN_METEO_URL.formatted(lat, lon);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Open-Meteo HTTP " + response.statusCode());
        }

        JsonNode daily  = MAPPER.readTree(response.body()).get("daily");
        JsonNode times  = daily.get("time");
        JsonNode codes  = daily.get("weathercode");
        JsonNode maxT   = daily.get("temperature_2m_max");
        JsonNode minT   = daily.get("temperature_2m_min");
        JsonNode precip = daily.get("precipitation_sum");

        List<DailyForecast> result = new ArrayList<>(times.size());
        for (int i = 0; i < times.size(); i++) {
            result.add(new DailyForecast(
                    LocalDate.parse(times.get(i).asText()),
                    codes.get(i).asInt(),
                    (int) Math.round(maxT.get(i).asDouble()),
                    (int) Math.round(minT.get(i).asDouble()),
                    precip.get(i).asDouble()
            ));
        }
        return result;
    }
}
