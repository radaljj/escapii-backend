package com.escapii.service.impl;

import com.escapii.model.Destination;
import com.escapii.repository.DestinationRepository;
import com.escapii.service.AirportLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.event.EventListener;
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
import java.util.Map;
import java.util.Objects;
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
 * <p><b>Zašto su dva partnera brza a jedan spor.</b> Holafly objavljuje sitemap svojih
 * eSIM stranica (~50 KB) a Bounce spisak gradova (~0,6 MB) - to se skine za tren i radi
 * se odmah pri čuvanju destinacije. GetYourGuide nema mali spisak: njihov sitemap
 * gradova je ~96 MB u četiri dela, pa se GYG slug popunjava naknadno, u pozadini, da
 * admin ne čeka.
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
    /** Holafly: jedna stranica po državi (uz poneku regiju i grad), npr. .../esim-italy/ */
    private static final Pattern HOLAFLY_DRZAVA = Pattern.compile("esim\\.holafly\\.com/(esim-[a-z0-9-]+)/?<");

    /**
     * Države koje Holafly piše drugačije nego airports.dat. Ključ je naš kebab-case
     * engleskog imena, vrednost njihov deo sluga (bez {@code esim-}). Provereno u
     * njihovom sitemapu 2026-10-01; država koje nema ni tamo ni ovde (Kuba, Liban,
     * Zelenortska Ostrva) prosto nema karticu.
     */
    private static final Map<String, String> HOLAFLY_IMENA = Map.of(
            "united-states",        "usa",
            "united-arab-emirates", "arab-emirates",
            "macedonia",            "north-macedonia"
    );
    private static final Pattern BOUNCE_GRAD   = Pattern.compile("href=\"/luggage-storage/([^\"/]+)\"");

    private final DestinationRepository destinationRepository;
    /** Englesko ime grada/države iz IATA koda - iz njega se izvode slugovi. */
    private final AirportLookupService airportLookupService;

    @Value("${app.affiliate.holafly-sitemap:https://esim.holafly.com/product-sitemap.xml}")
    private String holaflySitemap;
    @Value("${app.affiliate.bounce-cities:https://bounce.com/cities}")
    private String bounceCities;
    @Value("${app.affiliate.gyg-sitemap-index:https://www.getyourguide.com/sitemap.xml}")
    private String gygSitemapIndex;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private Set<String> holaflyKes; private Instant holaflyUzet;
    private Set<String> bounceKes;  private Instant bounceUzet;
    /** GYG prolaz je skup; ne sme da se pokrene dvaput uporedo. */
    private final AtomicBoolean gygUTeku = new AtomicBoolean(false);

    // ── Javni ulaz ───────────────────────────────────────────────────────────

    /**
     * Popunjava ono što se dobija brzo (Holafly, Bounce). Zove se pri čuvanju
     * destinacije. Ne baca izuzetak - nedostupan partnerski spisak ne sme da
     * obori čuvanje destinacije, polje prosto ostane prazno za sledeći put.
     *
     * @return da li se nešto promenilo (startni prolaz upisuje samo tada)
     */
    public boolean popuniBrzeSlugove(Destination d) {
        String holafly = d.getHolaflySlug();
        String bounce = d.getBounceSlug();
        Boolean pokriven = d.getBounceCovered();
        try {
            popuniHolafly(d);
            popuniBounce(d);
        } catch (Exception e) {
            log.warn("[Slugovi] Brzo popunjavanje nije uspelo za '{}': {}", d.getName(), e.toString());
        }
        return !Objects.equals(holafly, d.getHolaflySlug())
                || !Objects.equals(bounce, d.getBounceSlug())
                || !Objects.equals(pokriven, d.getBounceCovered());
    }

    /** Brz slug koji fali vredi pokušati ponovo (nov partner, spisak ranije nedostupan). */
    private static boolean faliBrzSlug(Destination d) {
        return prazno(d.getHolaflySlug()) || prazno(d.getBounceSlug());
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

    private void popuniHolafly(Destination d) {
        if (!prazno(d.getHolaflySlug())) return;
        String kandidat = holaflySlugZa(d.getCountryEn());
        if (kandidat == null) return;

        Set<String> spisak = holaflySpisak();
        if (spisak.isEmpty()) return;              // spisak nedostupan - ne nagađaj
        if (spisak.contains(kandidat)) {
            d.setHolaflySlug(kandidat);
            log.info("[Slugovi] {} -> holafly_slug={}", d.getName(), kandidat);
        } else {
            log.info("[Slugovi] {} - '{}' nije na Holafly spisku, ostaje prazno",
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
    protected synchronized Set<String> holaflySpisak() {
        if (holaflyKes == null || sveze(holaflyUzet)) {
            Set<String> s = new HashSet<>();
            skeniraj(holaflySitemap, HOLAFLY_DRZAVA, s::add);
            if (!s.isEmpty()) { holaflyKes = s; holaflyUzet = Instant.now(); }
        }
        return holaflyKes == null ? Set.of() : holaflyKes;
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

    // ── Samoisceljenje: imena iz koda + zastareli slugovi, bez admina ────────
    //
    // Slugovi zavise od engleskog imena, a ono od IATA koda i tabele ispravki u
    // AirportLookupService. Kad se bilo šta od toga promeni (admin promeni kod,
    // ispravka se doda u kod), zapis u bazi ostane star. Tri ulaza to popravljaju:
    // izmena destinacije u panelu, reveal koji naiđe na zastareo slug (u pozadini),
    // i prolaz kroz sve destinacije pri svakom startu.

    /** Ponovo izvodi englesko ime grada/države iz IATA koda. Vraća da li se nešto promenilo. */
    public boolean osveziImenaIzKoda(Destination d) {
        if (airportLookupService == null || prazno(d.getAirportCode())) return false;
        String grad   = airportLookupService.cityEn(d.getAirportCode()).orElse(null);
        String drzava = airportLookupService.countryEn(d.getAirportCode()).orElse(null);
        boolean menjano = false;
        if (grad != null && !grad.equals(d.getNameEn())) {
            log.info("[Slugovi] {} - englesko ime grada '{}' -> '{}' (iz koda {})", d.getName(), d.getNameEn(), grad, d.getAirportCode());
            d.setNameEn(grad);
            menjano = true;
        }
        if (drzava != null && !drzava.equals(d.getCountryEn())) {
            log.info("[Slugovi] {} - engleska država '{}' -> '{}' (iz koda {})", d.getName(), d.getCountryEn(), drzava, d.getAirportCode());
            d.setCountryEn(drzava);
            menjano = true;
        }
        return menjano;
    }

    /**
     * Jedna destinacija, u pozadini: imena iz koda, brisanje zastarelih, brzo
     * popunjavanje, pa GYG ako fali. Zove ga reveal kad naiđe na zastareo slug -
     * ta poseta još ne vidi kartice, sledeća ih dobija.
     */
    @Async("taskExecutor")
    @Transactional
    @CacheEvict(value = {"destinations", "destinations-by-airport"}, allEntries = true)
    public void osveziDestinacijuUPozadini(Long id) {
        try {
            Destination d = destinationRepository.findById(id).orElse(null);
            if (d == null) return;
            boolean menjano = osveziImenaIzKoda(d) | ocistiZastarele(d);
            if (!menjano) return;
            popuniBrzeSlugove(d);
            destinationRepository.save(d);
            log.info("[Slugovi] {} osvežena u pozadini (gyg={}, bounce={}, holafly={})",
                    d.getName(), d.getGygSlug(), d.getBounceSlug(), d.getHolaflySlug());
            if (prazno(d.getGygSlug())) popuniGygSlugoveUPozadini();
        } catch (Exception e) {
            log.warn("[Slugovi] Osvežavanje destinacije id={} nije uspelo: {}", id, e.toString());
        }
    }

    /**
     * Pri svakom startu, u pozadini: sve destinacije dobiju ime iz koda (i iz
     * novih ispravki), zastareli slugovi se obrišu i popune ponovo, a brz slug koji
     * fali se pokuša ponovo. Zbog ovoga deploy sa novom ispravkom (npr. MXP -> Milan)
     * ili sa novim partnerom popravi bazu sam, bez klika u panelu. Jeftino: spiskovi
     * partnera su keširani; GYG ide samo ako nekome fali.
     */
    @Async("taskExecutor")
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    @CacheEvict(value = {"destinations", "destinations-by-airport"}, allEntries = true)
    public void osveziSveNaStartu() {
        try {
            List<Destination> sve = destinationRepository.findAll();
            int osvezeno = 0;
            for (Destination d : sve) {
                boolean menjano = osveziImenaIzKoda(d) | ocistiZastarele(d);
                // Prazan brz slug se pokušava i kad se ništa nije menjalo: nov partner
                // (Holafly od 2026-10 - postojeće destinacije nemaju njegov slug) ili
                // spisak koji je pri čuvanju bio nedostupan. Upis samo ako je nešto popunjeno.
                if (menjano || faliBrzSlug(d)) {
                    menjano = popuniBrzeSlugove(d) || menjano;
                }
                if (!menjano) continue;
                destinationRepository.save(d);
                osvezeno++;
            }
            boolean nekomeFaliGyg = sve.stream().anyMatch(d -> prazno(d.getGygSlug()) && !prazno(d.getNameEn()));
            log.info("[Slugovi] Startni prolaz: {} destinacija, {} osveženo, GYG prolaz: {}", sve.size(), osvezeno, nekomeFaliGyg ? "da" : "ne");
            if (nekomeFaliGyg) popuniGygSlugoveUPozadini();
        } catch (Exception e) {
            log.warn("[Slugovi] Startni prolaz pao: {}", e.toString());
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

    /** Holafly slug je "esim-" + kebab-case engleskog imena DRŽAVE (uz tabelu izuzetaka). */
    public static boolean holaflyVaziZa(String slug, String countryEn) {
        if (prazno(slug)) return false;
        if (prazno(countryEn)) return true;
        return slug.trim().toLowerCase(Locale.ROOT).equals(holaflySlugZa(countryEn));
    }

    /** Očekivani Holafly slug za državu ({@code esim-italy}), ili null kad imena nema. */
    public static String holaflySlugZa(String countryEn) {
        String k = kljuc(countryEn);
        if (k.isEmpty()) return null;
        return "esim-" + HOLAFLY_IMENA.getOrDefault(k, k);
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
        if (!prazno(d.getHolaflySlug()) && !holaflyVaziZa(d.getHolaflySlug(), d.getCountryEn())) {
            log.info("[Slugovi] {} - holafly_slug '{}' ne odgovara državi '{}', brišem", d.getName(), d.getHolaflySlug(), d.getCountryEn());
            d.setHolaflySlug(null);
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
