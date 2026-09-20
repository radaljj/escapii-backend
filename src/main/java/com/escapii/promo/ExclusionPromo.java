package com.escapii.promo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Promo „besplatno isključivanje destinacija": jedan zajednički kod koji kupac ukuca u polje za
 * vaučer, a obračun cene ga primeni tako što naplativa isključivanja koštaju 0 €.
 *
 * <p>Kod, datum isteka i prekidač se menjaju iz admin panela (tabela {@code app_settings}), bez
 * deploya: promo traje mesec-dva, a ako kod procuri mora da može da se ugasi odmah. Dok niko ništa
 * nije sačuvao važe podrazumevane vrednosti iz application.properties - kod postoji, ali je promo
 * UGAŠEN.
 *
 * <p>Cenu i dalje računa samo backend: sajt pošalje kod uz pregled cene i uz rezervaciju, a ovde se
 * oba puta proveri da li kod još važi. Sam kod se nikad ne vraća javnim odgovorima.
 */
@Slf4j
@Component
public class ExclusionPromo {

    static final String K_KOD      = "promo.exclusions.code";
    static final String K_VAZI_DO  = "promo.exclusions.validUntil";
    static final String K_UKLJUCEN = "promo.exclusions.enabled";

    private static final ZoneId ZONA = ZoneId.of("Europe/Belgrade");
    private static final long KES_MS = 30_000;

    /** Slova, cifre, crtica i donja crta; 3-40 znakova. Vaučeri su ESC-XXXX-..., pa promo ne sme tako da počne. */
    private static final Pattern OBLIK_KODA = Pattern.compile("[A-Z0-9][A-Z0-9_-]{2,39}");

    /** Poruka kad kod uz rezervaciju više ne važi - sajt po njoj skida promo i osvežava cenu. */
    public static final String PORUKA_NE_VAZI =
            "Promo kod više ne važi. Cena je osvežena bez njega - proveri je i pošalji ponovo.";

    /** @param vaziDo poslednji dan kad kod važi (uključivo); null = datum nije određen, pa promo ne radi */
    public record Podesavanja(String kod, LocalDate vaziDo, boolean ukljucen) {
        public boolean aktivan(LocalDate danas) {
            return ukljucen && kod != null && !kod.isBlank() && vaziDo != null && !danas.isAfter(vaziDo);
        }
    }

    private final JdbcTemplate jdbc;
    private final String podrazumevaniKod;
    private final Supplier<LocalDate> danas;

    private volatile Podesavanja kes;
    private volatile long kesOd;

    @Autowired
    public ExclusionPromo(JdbcTemplate jdbc, @Value("${app.promo.exclusions.code:SKIP3}") String podrazumevaniKod) {
        this(jdbc, podrazumevaniKod, () -> LocalDate.now(ZONA));
    }

    ExclusionPromo(JdbcTemplate jdbc, String podrazumevaniKod, Supplier<LocalDate> danas) {
        this.jdbc = jdbc;
        this.podrazumevaniKod = normalizuj(podrazumevaniKod);
        this.danas = danas;
    }

    private static String normalizuj(String kod) {
        return kod == null ? "" : kod.strip().toUpperCase(Locale.ROOT);
    }

    /** Trenutna podešavanja (keš 30 s). Ako baza ne odgovori: poslednje poznato, a bez toga ugašeno. */
    public Podesavanja podesavanja() {
        Podesavanja p = kes;
        if (p != null && System.currentTimeMillis() - kesOd < KES_MS) return p;
        try {
            Map<String, String> v = new HashMap<>();
            jdbc.query("SELECT setting_key, setting_value FROM app_settings WHERE setting_key LIKE 'promo.exclusions.%'",
                    rs -> { v.put(rs.getString(1), rs.getString(2)); });
            String kod = v.containsKey(K_KOD) ? normalizuj(v.get(K_KOD)) : podrazumevaniKod;
            LocalDate vaziDo = parsirajDatum(v.get(K_VAZI_DO));
            boolean ukljucen = "true".equalsIgnoreCase(v.get(K_UKLJUCEN));
            p = new Podesavanja(kod, vaziDo, ukljucen);
            kes = p;
            kesOd = System.currentTimeMillis();
            return p;
        } catch (Exception e) {
            log.warn("[Promo] Podešavanja se ne mogu pročitati ({}) - {}", e.toString(),
                    p != null ? "koristim poslednja poznata" : "promo se tretira kao ugašen");
            return p != null ? p : new Podesavanja(podrazumevaniKod, null, false);
        }
    }

    private static LocalDate parsirajDatum(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.strip()); } catch (Exception e) { return null; }
    }

    /** Da li promo trenutno traje (uključen, ima kod i datum isteka nije prošao). */
    public boolean aktivan() {
        return podesavanja().aktivan(danas.get());
    }

    /** Da li je uneti kod važeći promo kod. Poređenje ne razlikuje velika i mala slova ni razmake okolo. */
    public boolean vazi(String uneto) {
        if (uneto == null || uneto.isBlank()) return false;
        Podesavanja p = podesavanja();
        return p.aktivan(danas.get()) && p.kod().equals(normalizuj(uneto));
    }

    public LocalDate danas() {
        return danas.get();
    }

    /** Čuva podešavanja iz panela. Baca IllegalArgumentException sa porukom za admina kad unos nije dobar. */
    public Podesavanja sacuvaj(String kod, LocalDate vaziDo, boolean ukljucen) {
        String k = normalizuj(kod);
        if (!OBLIK_KODA.matcher(k).matches()) {
            throw new IllegalArgumentException("Kod sme da ima 3-40 znakova: slova, cifre, crticu i donju crtu.");
        }
        if (k.startsWith("ESC-")) {
            throw new IllegalArgumentException("Kod ne sme da počinje sa ESC- (tako počinju poklon vaučeri).");
        }
        if (ukljucen && vaziDo == null) {
            throw new IllegalArgumentException("Da bi promo bio uključen, mora da ima datum do kog važi.");
        }
        upisi(K_KOD, k);
        upisi(K_VAZI_DO, vaziDo != null ? vaziDo.toString() : "");
        upisi(K_UKLJUCEN, String.valueOf(ukljucen));
        kes = null;
        log.info("[Promo] Sačuvano: kod={}, važi do={}, uključen={}", k, vaziDo, ukljucen);
        return podesavanja();
    }

    private void upisi(String kljuc, String vrednost) {
        jdbc.update("INSERT INTO app_settings (setting_key, setting_value, updated_at) VALUES (?, ?, ?) "
                  + "ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value, updated_at = EXCLUDED.updated_at",
                kljuc, vrednost, Timestamp.valueOf(LocalDateTime.now()));
    }
}
