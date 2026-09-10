package com.escapii.service.impl;

import com.escapii.model.Destination;
import com.escapii.repository.DestinationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Popunjava partnerske slugove za destinaciju, da ih admin ne bi kucao rukom.
 *
 * <p>Slugove ne izmišljamo - izvodimo ih iz engleskog naziva grada i države, pa
 * <b>proveravamo da li stvarno postoje</b> u javnim spiskovima partnera. Slug koji
 * nije proveren ne upisujemo: bolje prazno polje (kartica izostane) nego link koji
 * vodi na 404.
 *
 * <p><b>Zašto su dva partnera brza a jedan spor.</b> Airalo objavljuje spisak država
 * (~1 MB) a Bounce spisak gradova (~0,6 MB) - to se skine za tren i radi se odmah pri
 * čuvanju destinacije. GetYourGuide nema mali spisak: njihov sitemap gradova je ~96 MB
 * u četiri dela, pa se GYG slug popunjava naknadno, u pozadini, da admin ne čeka.
 * Njihov API bi ovo rešio jednim pozivom ({@code /1/locations?q=Prague}), ali traži
 * pristupni token koji se dobija tek na 100.000+ poseta mesečno.
 *
 * <p><b>Šta se čuva.</b> Samo slug za destinacije koje stvarno koristimo. Sitemap se
 * čita u prolazu i odbacuje - ne pravimo kopiju partnerskog kataloga.
 *
 * <p><b>Ručno uneta vrednost se nikad ne pregazi.</b> Popunjava se samo prazno polje.
 * Izuzetak je Bounce pokrivenost: ona je činjenica o partneru koja se menja (otvaraju
 * nove gradove), pa se osvežava i kad je slug već upisan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerSlugFiller {

    private static final Duration SVEZINA = Duration.ofHours(24);
    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    /** Partneri traže kontakt u User-Agent zaglavlju; adresa mora stvarno primati poštu. */
    private static final String USER_AGENT = "Escapii/1.0 (+https://escapii.rs; info@escapii.rs)";

    private static final Pattern LOC       = Pattern.compile("<loc>([^<]+)</loc>");
    private static final Pattern GYG_GRAD  = Pattern.compile("getyourguide\\.com/([a-z0-9-]+-l\\d+)/?<");
    private static final Pattern AIRALO_DRZAVA = Pattern.compile("airalo\\.com/([a-z0-9-]+-esim)/?<");
    private static final Pattern BOUNCE_GRAD   = Pattern.compile("href=\"/luggage-storage/([^\"/]+)\"");

    private final DestinationRepository destinationRepository;

    @Value("${app.affiliate.airalo-sitemap:https://www.airalo.com/sitemap-v2-countries.xml}")
    private String airaloSitemap;
    @Value("${app.affiliate.bounce-cities:https://bounce.com/cities}")
    private String bounceCities;
    @Value("${app.affiliate.gyg-sitemap-index:https://www.getyourguide.com/sitemap.xml}")
    private String gygSitemapIndex;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private Set<String> airaloKes;  private Instant airaloUzet;
    private Set<String> bounceKes;  private Instant bounceUzet;
    /** GYG prolaz je skup; ne sme da se pokrene dvaput uporedo. */
    private final AtomicBoolean gygUTeku = new AtomicBoolean(false);

    // ── Javni ulaz ───────────────────────────────────────────────────────────

    /**
     * Popunjava ono što se dobija brzo (Airalo, Bounce). Zove se pri čuvanju
     * destinacije. Ne baca izuzetak - nedostupan partnerski spisak ne sme da
     * obori čuvanje destinacije, polje prosto ostane prazno za sledeći put.
     */
    public void popuniBrzeSlugove(Destination d) {
        try {
            popuniAiralo(d);
            popuniBounce(d);
        } catch (Exception e) {
            log.warn("[Slugovi] Brzo popunjavanje nije uspelo za '{}': {}", d.getName(), e.toString());
        }
    }

    /**
     * Popunjava GYG slugove za sve destinacije kojima fale. Ide u pozadini jer
     * traži prolaz kroz ~96 MB sitemapa. Jedan prolaz rešava sve koje fale, pa
     * dodavanje pet destinacija zaredom ne znači pet skidanja.
     */
    @Async("taskExecutor")
    @Transactional
    public void popuniGygSlugoveUPozadini() {
        if (!gygUTeku.compareAndSet(false, true)) {
            log.info("[Slugovi] GYG prolaz je već u toku - preskačem");
            return;
        }
        try {
            List<Destination> bezSluga = destinationRepository.findAll().stream()
                    .filter(d -> prazno(d.getGygSlug()))
                    .toList();
            if (bezSluga.isEmpty()) return;

            log.info("[Slugovi] GYG prolaz počinje, destinacija bez sluga: {}", bezSluga.size());
            Set<String> traziMo = new HashSet<>();
            for (Destination d : bezSluga) {
                String k = kljuc(d.getNameEn());
                if (!k.isEmpty()) traziMo.add(k);
            }

            java.util.Map<String, String> nadjeno = new java.util.HashMap<>();
            for (String shard : gygShardovi()) {
                skeniraj(shard, GYG_GRAD, segment -> {
                    String grad = segment.replaceAll("-l\\d+$", "");
                    if (traziMo.contains(grad)) nadjeno.putIfAbsent(grad, segment);
                });
                // Nema svrhe skidati ostale delove ako smo sve našli.
                if (nadjeno.size() == traziMo.size()) break;
            }

            int upisano = 0;
            for (Destination d : bezSluga) {
                String slug = nadjeno.get(kljuc(d.getNameEn()));
                if (slug != null) {
                    d.setGygSlug(slug);
                    destinationRepository.save(d);
                    upisano++;
                    log.info("[Slugovi] {} -> gyg_slug={}", d.getName(), slug);
                }
            }
            log.info("[Slugovi] GYG prolaz gotov: {}/{} popunjeno", upisano, bezSluga.size());
        } catch (Exception e) {
            log.warn("[Slugovi] GYG prolaz pao: {}", e.toString());
        } finally {
            gygUTeku.set(false);
        }
    }

    // ── Pojedinačni partneri ─────────────────────────────────────────────────

    private void popuniAiralo(Destination d) {
        if (!prazno(d.getAiraloSlug())) return;
        String kandidat = kljuc(d.getCountryEn());
        if (kandidat.isEmpty()) return;
        kandidat = kandidat + "-esim";

        Set<String> spisak = airaloSpisak();
        if (spisak.isEmpty()) return;              // spisak nedostupan - ne nagađaj
        if (spisak.contains(kandidat)) {
            d.setAiraloSlug(kandidat);
            log.info("[Slugovi] {} -> airalo_slug={}", d.getName(), kandidat);
        } else {
            log.info("[Slugovi] {} - '{}' nije na Airalo spisku, ostaje prazno",
                    d.getName(), kandidat);
        }
    }

    private void popuniBounce(Destination d) {
        Set<String> spisak = bounceSpisak();
        if (spisak.isEmpty()) return;

        if (prazno(d.getBounceSlug())) {
            String kandidat = kljuc(d.getNameEn());
            if (kandidat.isEmpty()) return;
            if (spisak.contains(kandidat)) {
                d.setBounceSlug(kandidat);
                d.setBounceCovered(true);
                log.info("[Slugovi] {} -> bounce_slug={}", d.getName(), kandidat);
            } else {
                d.setBounceCovered(false);
                log.info("[Slugovi] {} - Bounce nema lokacije u tom gradu", d.getName());
            }
            return;
        }
        // Slug je već upisan (rucno ili ranije): osvežavamo samo pokrivenost, jer
        // Bounce vremenom otvara nove gradove i zatvara stare.
        boolean pokriven = spisak.contains(d.getBounceSlug().trim().toLowerCase(Locale.ROOT));
        if (!pokriven == Boolean.TRUE.equals(d.getBounceCovered())) {
            d.setBounceCovered(pokriven);
            log.info("[Slugovi] {} - Bounce pokrivenost promenjena na {}", d.getName(), pokriven);
        }
    }

    // ── Spiskovi partnera (keširani) ─────────────────────────────────────────

    /** protected radi testa - test podmece spisak umesto mreznog poziva. */
    protected synchronized Set<String> airaloSpisak() {
        if (airaloKes == null || sveze(airaloUzet)) {
            Set<String> s = new HashSet<>();
            skeniraj(airaloSitemap, AIRALO_DRZAVA, s::add);
            if (!s.isEmpty()) { airaloKes = s; airaloUzet = Instant.now(); }
        }
        return airaloKes == null ? Set.of() : airaloKes;
    }

    protected synchronized Set<String> bounceSpisak() {
        if (bounceKes == null || sveze(bounceUzet)) {
            Set<String> s = new HashSet<>();
            skeniraj(bounceCities, BOUNCE_GRAD, s::add);
            if (!s.isEmpty()) { bounceKes = s; bounceUzet = Instant.now(); }
        }
        return bounceKes == null ? Set.of() : bounceKes;
    }

    private boolean sveze(Instant kad) {
        return kad == null || Instant.now().isAfter(kad.plus(SVEZINA));
    }

    private List<String> gygShardovi() {
        List<String> out = new java.util.ArrayList<>();
        skeniraj(gygSitemapIndex, LOC, url -> {
            if (url.contains("sitemap-city")) out.add(url);
        });
        return out;
    }

    /**
     * Čita odgovor red po red i predaje svako poklapanje potrošaču. Namerno
     * streaming: GYG shard je ~24 MB, a učitavanje celog u memoriju na malom
     * serveru je nepotrebno.
     */
    private void skeniraj(String url, Pattern obrazac, java.util.function.Consumer<String> naPogodak) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(TIMEOUT)
                    .GET().build();
            HttpResponse<java.io.InputStream> res =
                    http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                log.warn("[Slugovi] {} vratio HTTP {}", url, res.statusCode());
                return;
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(res.body(), StandardCharsets.UTF_8), 1 << 16)) {
                String line;
                while ((line = r.readLine()) != null) {
                    Matcher m = obrazac.matcher(line);
                    while (m.find()) naPogodak.accept(m.group(1));
                }
            }
        } catch (Exception e) {
            log.warn("[Slugovi] Čitanje {} nije uspelo: {}", url, e.toString());
        }
    }

    // ── Doslednost sluga sa trenutnim IATA kodom ─────────────────────────────
    //
    // Slug je izveden iz engleskog imena grada/države, a ono iz IATA koda. Kad
    // admin promeni kod (Milano: BGY Bergamo -> MXP Malpensa), staro ime nestane,
    // ali slugovi "bergamo" ostanu i linkovi vode u pogrešan grad. Pravilo
    // "ručno uneto se ne pregazi" tu ne štiti ništa - panel nema polja za slugove -
    // a štetu pravi. Zato: slug koji ne odgovara TRENUTNOM imenu je zastareo.
    // Kad je ime nepoznato (nema ga u airports.dat), nema osnova za sud - slug ostaje.

    /** GYG slug "florence-l32" važi za grad "Florence"; "bergamo-l123" za "Milan" ne. */
    public static boolean gygVaziZa(String slug, String nameEn) {
        if (prazno(slug)) return false;
        if (prazno(nameEn)) return true;
        String grad = slug.trim().toLowerCase(Locale.ROOT).replaceAll("-l\\d+$", "");
        return grad.equals(kljuc(nameEn));
    }

    /** Bounce slug je tačno kebab-case engleskog imena grada. */
    public static boolean bounceVaziZa(String slug, String nameEn) {
        if (prazno(slug)) return false;
        if (prazno(nameEn)) return true;
        return slug.trim().toLowerCase(Locale.ROOT).equals(kljuc(nameEn));
    }

    /** Airalo slug je kebab-case engleskog imena DRŽAVE + "-esim". */
    public static boolean airaloVaziZa(String slug, String countryEn) {
        if (prazno(slug)) return false;
        if (prazno(countryEn)) return true;
        return slug.trim().toLowerCase(Locale.ROOT).equals(kljuc(countryEn) + "-esim");
    }

    /**
     * Briše slugove koji ne odgovaraju trenutnom engleskom imenu grada/države,
     * da bi ih popunjavanje ispod izvelo ponovo iz novog koda. Vraća da li je
     * nešto obrisano. Zove se pri izmeni destinacije, PRE popunjavanja.
     */
    public boolean ocistiZastarele(Destination d) {
        boolean menjano = false;
        if (!prazno(d.getGygSlug()) && !gygVaziZa(d.getGygSlug(), d.getNameEn())) {
            log.info("[Slugovi] {} - gyg_slug '{}' ne odgovara gradu '{}', brišem", d.getName(), d.getGygSlug(), d.getNameEn());
            d.setGygSlug(null);
            menjano = true;
        }
        if (!prazno(d.getBounceSlug()) && !bounceVaziZa(d.getBounceSlug(), d.getNameEn())) {
            log.info("[Slugovi] {} - bounce_slug '{}' ne odgovara gradu '{}', brišem", d.getName(), d.getBounceSlug(), d.getNameEn());
            d.setBounceSlug(null);
            d.setBounceCovered(false);
            menjano = true;
        }
        if (!prazno(d.getAiraloSlug()) && !airaloVaziZa(d.getAiraloSlug(), d.getCountryEn())) {
            log.info("[Slugovi] {} - airalo_slug '{}' ne odgovara državi '{}', brišem", d.getName(), d.getAiraloSlug(), d.getCountryEn());
            d.setAiraloSlug(null);
            menjano = true;
        }
        return menjano;
    }
    // ── Pomoćno ──────────────────────────────────────────────────────────────

    /** Skida dijakritiku i svodi na kebab-case, isto kako partneri prave slugove. */
    public static String kljuc(String s) {
        if (s == null) return "";
        String bezKvacica = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d").replace("Đ", "D")
                .replace("ø", "o").replace("Ø", "O")
                .replace("ß", "ss");
        return bezKvacica.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static boolean prazno(String s) {
        return s == null || s.isBlank();
    }
}
