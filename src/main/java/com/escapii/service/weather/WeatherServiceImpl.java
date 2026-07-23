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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class WeatherServiceImpl implements WeatherService {

    // ── Konstante ─────────────────────────────────────────────────────────────

    private static final String NOMINATIM_URL =
            "https://nominatim.openstreetmap.org/search?q=%s&format=json&limit=1";

    private static final String OPEN_METEO_URL =
            "https://api.open-meteo.com/v1/forecast"
            + "?latitude=%.4f&longitude=%.4f"
            + "&daily=weathercode,temperature_2m_max,temperature_2m_min,precipitation_sum"
            + "&timezone=auto&forecast_days=16";

    private static final String MET_NORWAY_URL =
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f";

    private static final String USER_AGENT = "Escapii/1.0 (contact@escapii.com)";
    private static final int    TIMEOUT_SEC = 10;

    // Open-Meteo/Nominatim povremeno vrate 503 (privremeno preopterećenje) -
    // to je tranzijentno, traje sekunde. Bez ponavljanja jedan takav odgovor
    // obori prognozu za ceo dan; ako je to poslednji dan pre polaska, zauvek.
    private static final int  MAX_ATTEMPTS   = 3;
    private static final long RETRY_BASE_MS  = 1200;

    // ── Statičke instance (thread-safe) ───────────────────────────────────────

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SEC))
            .build();

    // ── Javni API ─────────────────────────────────────────────────────────────

    @Override
    public Optional<List<DailyForecast>> getForecast(String cityName) {
        double[] coords;
        try {
            coords = geocode(cityName);
        } catch (Exception e) {
            log.error("[Weather] Geocoding pao za '{}': {}", cityName, e.getMessage());
            return Optional.empty();
        }
        if (coords == null) {
            log.warn("[Weather] Geocoding nije vratio rezultat za: '{}'", cityName);
            return Optional.empty();
        }

        // Primarni izvor: Open-Meteo (nemačka firma).
        try {
            List<DailyForecast> f = fetchOpenMeteo(coords[0], coords[1]);
            log.info("[Weather] Prognoza (Open-Meteo) za '{}' → dana={}", cityName, f.size());
            return Optional.of(f);
        } catch (Exception e) {
            log.warn("[Weather] Open-Meteo pao za '{}': {} → prelazim na rezervni (MET Norway)",
                    cityName, e.getMessage());
        }

        // Rezervni izvor: MET Norway (norveški državni institut) - zove se SAMO
        // ako je Open-Meteo pao. Da oba padnu istovremeno praktično je nemoguće -
        // različite firme, različita infrastruktura, različite države.
        try {
            List<DailyForecast> f = fetchMetNorway(coords[0], coords[1]);
            log.info("[Weather] Prognoza (MET Norway REZERVNI) za '{}' → dana={}", cityName, f.size());
            return Optional.of(f);
        } catch (Exception e) {
            log.error("[Weather] I rezervni izvor pao za '{}': {} - prognoza nedostupna",
                    cityName, e.getMessage(), e);
            return Optional.empty();
        }
    }

    // ── HTTP sa ponavljanjem ──────────────────────────────────────────────────

    /**
     * Šalje zahtev sa do {@link #MAX_ATTEMPTS} pokušaja. Ponavlja SAMO na
     * privremene greške: HTTP 5xx i mrežne (IOException). 4xx se ne ponavlja -
     * to je trajna greška (npr. nepostojeći grad), ponavljanje ne bi pomoglo.
     */
    private HttpResponse<String> sendWithRetry(HttpRequest request, String label) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                int sc = response.statusCode();
                if (sc >= 500 && attempt < MAX_ATTEMPTS) {
                    log.warn("[Weather] {} HTTP {} (pokušaj {}/{}) - ponavljam", label, sc, attempt, MAX_ATTEMPTS);
                    sleep(attempt);
                    continue;
                }
                return response; // 2xx, 4xx, ili poslednji pokušaj na 5xx
            } catch (java.io.IOException e) {
                lastError = e;
                if (attempt < MAX_ATTEMPTS) {
                    log.warn("[Weather] {} mrežna greška '{}' (pokušaj {}/{}) - ponavljam",
                            label, e.getMessage(), attempt, MAX_ATTEMPTS);
                    sleep(attempt);
                }
            }
        }
        throw lastError != null ? lastError
                : new RuntimeException(label + " nije uspeo posle " + MAX_ATTEMPTS + " pokušaja");
    }

    /** Rastuća pauza između pokušaja (1.2s, 2.4s...). */
    private void sleep(int attempt) {
        try {
            Thread.sleep(RETRY_BASE_MS * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
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

        HttpResponse<String> response = sendWithRetry(request, "Nominatim");

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

    /** Preuzima 16-dnevnu dnevnu prognozu. Nikad ne šalje ime grada ka API-ju. */
    private List<DailyForecast> fetchOpenMeteo(double lat, double lon) throws Exception {
        String url = OPEN_METEO_URL.formatted(lat, lon);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                .GET()
                .build();

        HttpResponse<String> response = sendWithRetry(request, "Open-Meteo");

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

    // ── Rezervna prognoza (MET Norway / yr.no) ────────────────────────────────

    /** Dnevni agregat - MET vraća satne tačke, grupišemo ih po danu. */
    private static final class DayAgg {
        double max = Double.NEGATIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;
        double precip = 0;
        String symbol = null;
    }

    /**
     * Rezervni izvor. MET Norway vraća satne tačke sa svojim oznakama vremena,
     * pa ih ovde grupišemo u dnevne (max/min temperatura, suma padavina) i
     * mapiramo oznaku na WMO kod koji mejl već koristi za emoji.
     */
    private List<DailyForecast> fetchMetNorway(double lat, double lon) throws Exception {
        String url = MET_NORWAY_URL.formatted(lat, lon);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                // User-Agent je OBAVEZAN za MET Norway - bez njega vraća 403.
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                .GET()
                .build();

        HttpResponse<String> response = sendWithRetry(request, "MET Norway");
        if (response.statusCode() != 200) {
            throw new RuntimeException("MET Norway HTTP " + response.statusCode());
        }

        JsonNode series = MAPPER.readTree(response.body()).path("properties").path("timeseries");

        // LinkedHashMap čuva hronološki redosled dana
        Map<LocalDate, DayAgg> byDay = new LinkedHashMap<>();
        for (JsonNode point : series) {
            OffsetDateTime t = OffsetDateTime.parse(point.path("time").asText());
            JsonNode instant = point.path("data").path("instant").path("details");
            if (!instant.has("air_temperature")) continue;

            double temp = instant.path("air_temperature").asDouble();
            JsonNode next1 = point.path("data").path("next_1_hours");
            double p = next1.path("details").path("precipitation_amount").asDouble(0.0);
            String symbol = next1.path("summary").path("symbol_code").asText("");

            DayAgg agg = byDay.computeIfAbsent(t.toLocalDate(), d -> new DayAgg());
            agg.max = Math.max(agg.max, temp);
            agg.min = Math.min(agg.min, temp);
            agg.precip += p;
            // Oznaka oko podneva najbolje opisuje dan; inače uzmi prvu dostupnu.
            int h = t.getHour();
            if ((h >= 11 && h <= 14 && !symbol.isBlank()) || (agg.symbol == null && !symbol.isBlank())) {
                agg.symbol = symbol;
            }
        }

        List<DailyForecast> result = new ArrayList<>(byDay.size());
        for (Map.Entry<LocalDate, DayAgg> e : byDay.entrySet()) {
            DayAgg a = e.getValue();
            if (a.max == Double.NEGATIVE_INFINITY) continue; // dan bez ijedne tačke
            result.add(new DailyForecast(
                    e.getKey(),
                    symbolToWmoCode(a.symbol),
                    (int) Math.round(a.max),
                    (int) Math.round(a.min),
                    Math.round(a.precip * 10.0) / 10.0
            ));
        }
        return result;
    }

    /**
     * MET koristi tekstualne oznake ("clearsky_day", "rain"...), a DailyForecast
     * očekuje WMO numerički kod. Mapiramo na najbliži - dovoljno za emoji/opis.
     */
    static int symbolToWmoCode(String symbol) {
        if (symbol == null || symbol.isBlank()) return 3; // nepoznato → oblačno
        String s = symbol.toLowerCase();
        if (s.contains("thunder"))                        return 95;
        if (s.contains("snow") || s.contains("sleet"))    return 71;
        if (s.contains("heavyrain"))                      return 65;
        if (s.contains("lightrain"))                      return 51;
        if (s.contains("rain") || s.contains("showers"))  return 61;
        if (s.contains("fog"))                            return 45;
        if (s.startsWith("cloudy"))                       return 3;
        if (s.contains("partlycloudy"))                   return 2;
        if (s.contains("fair"))                           return 1;
        if (s.contains("clearsky"))                       return 0;
        return 3;
    }
}
