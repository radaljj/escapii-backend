package com.escapii.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Parsira bundlovani airports.dat (OpenFlights) pri startu i omogućava
 * lookup engleskog naziva grada i države po IATA kodu.
 *
 * Format airports.dat (CSV, bez headera):
 * ID, Name, City, Country, IATA, ICAO, Lat, Lon, Alt, Tz, DST, TzDB, Type, Source
 */
@Slf4j
@Service
public class AirportLookupService {

    record AirportInfo(String cityEn, String countryEn) {}

    private final Map<String, AirportInfo> cache = new HashMap<>(8000);

    @PostConstruct
    void init() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("airports.dat").getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            int loaded = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = splitCsv(line);
                if (parts.length < 5) continue;
                String iata = parts[4].replace("\"", "").trim();
                if (iata.isEmpty() || iata.equals("\\N")) continue;
                String city    = parts[2].replace("\"", "").trim();
                String country = parts[3].replace("\"", "").trim();
                cache.put(iata.toUpperCase(), new AirportInfo(city, country));
                loaded++;
            }
            log.info("[AirportLookup] Učitano {} aerodroma iz airports.dat", loaded);
        } catch (Exception e) {
            log.error("[AirportLookup] Greška pri učitavanju airports.dat: {}", e.getMessage());
        }
    }

    public Optional<String> cityEn(String iataCode) {
        if (iataCode == null) return Optional.empty();
        AirportInfo info = cache.get(iataCode.toUpperCase());
        return info == null ? Optional.empty() : Optional.of(info.cityEn());
    }

    public Optional<String> countryEn(String iataCode) {
        if (iataCode == null) return Optional.empty();
        AirportInfo info = cache.get(iataCode.toUpperCase());
        return info == null ? Optional.empty() : Optional.of(info.countryEn());
    }

    private String[] splitCsv(String line) {
        // Naivni CSV split koji poštuje navodnike - dovoljno za airports.dat format
        java.util.List<String> fields = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
                sb.append(c);
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }
}
