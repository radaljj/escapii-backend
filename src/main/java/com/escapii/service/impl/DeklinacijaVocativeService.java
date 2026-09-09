package com.escapii.service.impl;

import com.escapii.service.VocativeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Vokativ preko deklinacija.com — {@code GET /api/{ime}} vraća
 * {@code {"sex","vocative","vocative_cyr","status"}}. Bez ključa, 25 zahteva u
 * sekundi po IP-u, besplatno i za komercijalnu upotrebu (stanje 2026-09-09).
 *
 * <p><b>Tri stvari koje sam izmerio pre nego što sam ovo napisao</b>, jer se
 * dokumentacija i ponašanje razilaze:
 * <ul>
 *   <li>Odgovor stiže za ~250–320 ms. Dovoljno brzo da se zove pri slanju, uz
 *       keš da se ime nikad ne pita dvaput.</li>
 *   <li>{@code "uros"} malim slovom vraća {@code "Not found"}, iako dokumentacija
 *       tvrdi da je pretraga case-insensitive. Kupac kuca kako hoće, pa se prvo
 *       slovo diže ovde, pre poziva.</li>
 *   <li>Nepoznato ime nije greška nego {@code status: "Not found"} sa
 *       {@code vocative: null}. To se tretira isto kao svaka druga nemogućnost —
 *       nominativ.</li>
 * </ul>
 *
 * <p><b>Rok od 3 sekunde je ceo poziv</b>, ne samo zaglavlje. {@code sendAsync}
 * + {@code get(rok)} umesto {@code send()}: {@code HttpRequest.timeout()} u JDK
 * klijentu ograničava samo čekanje na zaglavlja, pa bi server koji pošalje
 * zaglavlja i zaćuti držao nit zauvek — ista lekcija kao {@code WeatherServiceImpl}.
 *
 * <p><b>Keš čuva i promašaje.</b> Ime koje servis ne zna vraća nominativ, i taj
 * nominativ se keširа kao odgovor — inače bi svako "Xyz" ponovo išlo na mrežu
 * pri svakom mejlu. Ključ je isečeno uneto ime; "uros" i "Uroš" su dva unosa, i
 * to je namerno jeftinije od normalizacije u SpEL-u.
 */
@Slf4j
@Service
public class DeklinacijaVocativeService implements VocativeService {

    private static final int TIMEOUT_SEC = 3;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SEC))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Prazno isključuje servis — svako ime ostaje u nominativu, bez ijednog poziva. */
    @Value("${app.vocative.url:https://deklinacija.com/api/}")
    private String apiUrl;

    @Override
    @Cacheable(value = "vocatives", key = "#firstName == null ? '' : #firstName.trim()")
    public String vocative(String firstName) {
        String ime = normalize(firstName);
        if (ime.isEmpty() || apiUrl == null || apiUrl.isBlank()) return ime;

        try {
            String url = apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + URLEncoder.encode(ime, StandardCharsets.UTF_8)))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Escapii/1.0 (vokativ za obracanje u mejlu)")
                    .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                    .GET()
                    .build();

            CompletableFuture<HttpResponse<String>> buducnost =
                    HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> response;
            try {
                response = buducnost.get(TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                buducnost.cancel(true);
                log.warn("[Vokativ] rok od {}s istekao za '{}' - ostaje nominativ", TIMEOUT_SEC, ime);
                return ime;
            }

            if (response.statusCode() != 200) {
                log.warn("[Vokativ] HTTP {} za '{}' - ostaje nominativ", response.statusCode(), ime);
                return ime;
            }
            return parse(response.body(), ime);

        } catch (Exception e) {
            // Sve, uključujući InterruptedException i loš JSON: obraćanje nikad ne
            // sme da bude razlog da mejl ne ode.
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("[Vokativ] '{}' nije razrešeno ({}) - ostaje nominativ", ime, e.toString());
            return ime;
        }
    }

    /**
     * Prvo ime, isečeno, sa velikim početnim slovom.
     *
     * <p>Uzima se samo prvi token: polje "ime" ume da stigne kao "Marko Petar", a
     * ime obdarenog uvek stiže kao "Ime Prezime" iz liste putnika. Vokativ prezimena
     * se u pozdravu ne koristi.
     *
     * <p>Veličina slova se dira samo kad je ceo unos jednoličan — "uros" ili "UROŠ"
     * postaje "Uroš", ali "Ana-Marija" ostaje kako je otkucano. Mešovit unos je
     * skoro uvek namerno takav.
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        int razmak = s.indexOf(' ');
        if (razmak > 0) s = s.substring(0, razmak);
        // zarez ili tačka koju kupac zalepi uz ime
        while (!s.isEmpty() && ",.;:".indexOf(s.charAt(s.length() - 1)) >= 0) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) return "";
        boolean jednolicno = s.equals(s.toLowerCase()) || s.equals(s.toUpperCase());
        if (jednolicno) {
            s = s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
        }
        return s;
    }

    /** {@code status == "Success"} i nepraznо {@code vocative} → vokativ; sve ostalo → nominativ. */
    public static String parse(String body, String nominativ) {
        try {
            JsonNode n = MAPPER.readTree(body);
            String status = n.path("status").asText("");
            String vok = n.path("vocative").isNull() ? "" : n.path("vocative").asText("");
            if ("Success".equalsIgnoreCase(status) && !vok.isBlank()) {
                return vok.trim();
            }
            return nominativ;
        } catch (Exception e) {
            return nominativ;
        }
    }
}
