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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

        Optional<String> vok = upit(ime);
        if (vok.isPresent()) return vok.get();

        // Servis ne zna ime. Najčešći razlog nije retko ime nego tastatura bez š/č/ć:
        // "Uros" vraća Not found, "Uroš" vraća Uroše. Izmereno: dj→đ servis sam
        // mapira, s→š ne. Probaju se varijante sa dijakritikom - prvo sa jednom
        // zamenom (skoro sva imena imaju tačno jednu), pa sa dve - i prva koju servis
        // prepozna pobeđuje. Bez pogotka ostaje nominativ, kako je otkucan.
        for (String varijanta : diacriticVariants(ime)) {
            Optional<String> v = upit(varijanta);
            if (v.isPresent()) {
                log.info("[Vokativ] '{}' razrešeno preko varijante '{}' -> '{}'", ime, varijanta, v.get());
                return v.get();
            }
        }
        return ime;
    }

    /**
     * Jedan poziv servisa. Prazno znači "nije razrešeno" iz bilo kog razloga -
     * Not found, rok, HTTP greška, loš JSON - i pozivalac odlučuje šta dalje.
     */
    private Optional<String> upit(String ime) {
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
                return Optional.empty();
            }
            if (response.statusCode() != 200) {
                log.warn("[Vokativ] HTTP {} za '{}' - ostaje nominativ", response.statusCode(), ime);
                return Optional.empty();
            }
            return parseOptional(response.body());
        } catch (Exception e) {
            // Sve, uključujući InterruptedException i loš JSON: obraćanje nikad ne
            // sme da bude razlog da mejl ne ode.
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("[Vokativ] '{}' nije razrešeno ({}) - ostaje nominativ", ime, e.toString());
            return Optional.empty();
        }
    }

    /** Najviše ovoliko mrežnih poziva po imenu, i to samo kad prvi ne nađe. */
    public static final int MAX_VARIJANTI = 8;

    /**
     * Varijante imena sa dijakritikom, poređane po broju zamena rastuće.
     *
     * <p>s→š, z→ž, c→č i c→ć, dj→đ. Velika slova se čuvaju: "SASA"→"SAŠA". Ime bez
     * ijednog kandidata daje praznu listu. Kod dva kandidata prvo idu sve varijante
     * sa jednom zamenom, pa one sa dve - "Sasa" daje [Šasa, Saša, Šaša], jer je
     * jedna zamena daleko najverovatniji slučaj.
     */
    public static List<String> diacriticVariants(String ime) {
        // pozicije kandidata: svaki element je (indeks, dužina, zamene)
        List<int[]> poz = new ArrayList<>();
        List<String[]> zamene = new ArrayList<>();
        for (int i = 0; i < ime.length(); i++) {
            char c = ime.charAt(i);
            char lc = Character.toLowerCase(c);
            boolean veliko = Character.isUpperCase(c);
            if (lc == 'd' && i + 1 < ime.length() && Character.toLowerCase(ime.charAt(i + 1)) == 'j') {
                poz.add(new int[]{i, 2}); zamene.add(new String[]{veliko ? "Đ" : "đ"}); i++;
            } else if (lc == 's') {
                poz.add(new int[]{i, 1}); zamene.add(new String[]{veliko ? "Š" : "š"});
            } else if (lc == 'z') {
                poz.add(new int[]{i, 1}); zamene.add(new String[]{veliko ? "Ž" : "ž"});
            } else if (lc == 'c') {
                poz.add(new int[]{i, 1}); zamene.add(new String[]{veliko ? "Č" : "č", veliko ? "Ć" : "ć"});
            }
        }
        if (poz.isEmpty()) return List.of();

        List<String> rezultat = new ArrayList<>();
        // po broju zamena: 1, pa 2 - dublje retko treba, a svaka varijanta je HTTP poziv
        for (int koliko = 1; koliko <= Math.min(2, poz.size()); koliko++) {
            kombinuj(ime, poz, zamene, 0, koliko, new ArrayList<>(), rezultat);
            if (rezultat.size() >= MAX_VARIJANTI) break;
        }
        return rezultat.size() > MAX_VARIJANTI ? rezultat.subList(0, MAX_VARIJANTI) : rezultat;
    }

    /** Sve varijante koje menjaju tačno {@code koliko} pozicija od {@code od} nadalje. */
    private static void kombinuj(String ime, List<int[]> poz, List<String[]> zamene,
                                 int od, int koliko, List<int[]> izbor, List<String> out) {
        if (koliko == 0) {
            out.add(primeni(ime, poz, zamene, izbor));
            return;
        }
        for (int i = od; i <= poz.size() - koliko; i++) {
            for (int z = 0; z < zamene.get(i).length; z++) {
                izbor.add(new int[]{i, z});
                kombinuj(ime, poz, zamene, i + 1, koliko - 1, izbor, out);
                izbor.remove(izbor.size() - 1);
            }
        }
    }

    private static String primeni(String ime, List<int[]> poz, List<String[]> zamene, List<int[]> izbor) {
        StringBuilder sb = new StringBuilder(ime);
        // zdesna nalevo, da se indeksi ne pomeraju
        for (int k = izbor.size() - 1; k >= 0; k--) {
            int pi = izbor.get(k)[0], zi = izbor.get(k)[1];
            int start = poz.get(pi)[0], len = poz.get(pi)[1];
            sb.replace(start, start + len, zamene.get(pi)[zi]);
        }
        return sb.toString();
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

    /** {@code status == "Success"} i neprazno {@code vocative} → vokativ; sve ostalo → nominativ. */
    public static String parse(String body, String nominativ) {
        return parseOptional(body).orElse(nominativ);
    }

    /** Kao {@link #parse}, ali razlikuje "nije razrešeno" od "vokativ je isti kao nominativ". */
    public static Optional<String> parseOptional(String body) {
        try {
            JsonNode n = MAPPER.readTree(body);
            String status = n.path("status").asText("");
            String vok = n.path("vocative").isNull() ? "" : n.path("vocative").asText("");
            if ("Success".equalsIgnoreCase(status) && !vok.isBlank()) {
                return Optional.of(vok.trim());
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
