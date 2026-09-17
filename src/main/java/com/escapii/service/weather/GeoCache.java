package com.escapii.service.weather;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Koordinate destinacija za prognozu, jednom geokodirane pa zapamćene: u memoriji (za ovaj
 * proces) i u tabeli {@code geo_cache} (preživljava restart). Jutarnji krug tako ne zavisi
 * od geokodera - Nominatim je bio jedina tačka pada ispred oba vremenska izvora.
 *
 * <p>Ključ je upit za prognozu (grad ili „Grad za prognozu" iz panela), normalizovan
 * (trim, mala slova). Baza je opciona: bez nje radi samo memorija (testovi).
 */
@Slf4j
@Component
public class GeoCache {

    static final int MAX_KLJUC = 200;

    private final JdbcTemplate jdbc;
    private final Map<String, double[]> memorija = new ConcurrentHashMap<>();

    @Autowired
    public GeoCache(ObjectProvider<JdbcTemplate> jdbc) {
        this(jdbc.getIfAvailable());
    }

    public GeoCache(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static String kljuc(String query) {
        if (query == null) return "";
        String k = query.strip().toLowerCase(Locale.ROOT);
        return k.length() > MAX_KLJUC ? k.substring(0, MAX_KLJUC) : k;
    }

    /** [lat, lon] ako je upit već geokodiran. Greška baze = promašaj, ne izuzetak. */
    public Optional<double[]> get(String query) {
        String k = kljuc(query);
        if (k.isEmpty()) return Optional.empty();
        double[] m = memorija.get(k);
        if (m != null) return Optional.of(m.clone());
        if (jdbc == null) return Optional.empty();
        try {
            List<double[]> r = jdbc.query("SELECT lat, lon FROM geo_cache WHERE city_query = ?",
                    (rs, i) -> new double[]{ rs.getDouble(1), rs.getDouble(2) }, k);
            if (r.isEmpty()) return Optional.empty();
            memorija.put(k, r.get(0));
            return Optional.of(r.get(0).clone());
        } catch (Exception e) {
            log.warn("[Weather] geo_cache čitanje nije uspelo za '{}': {}", k, e.toString());
            return Optional.empty();
        }
    }

    /** Pamti koordinate; pad upisa u bazu samo se loguje - memorija je već osvežena. */
    public void put(String query, double lat, double lon, String source) {
        put(query, lat, lon, source, true);
    }

    /**
     * @param trajno false = samo memorija (do restarta). Za rezervni geokoder, koji za srpske
     *               nazive ume da vrati pogrešno mesto - takav pogodak ne sme da ostane zauvek,
     *               sutra Nominatim dobija novu priliku.
     */
    public void put(String query, double lat, double lon, String source, boolean trajno) {
        String k = kljuc(query);
        if (k.isEmpty()) return;
        memorija.put(k, new double[]{ lat, lon });
        if (jdbc == null || !trajno) return;
        try {
            jdbc.update("INSERT INTO geo_cache (city_query, lat, lon, source, resolved_at) VALUES (?, ?, ?, ?, ?) "
                      + "ON CONFLICT (city_query) DO UPDATE SET lat = EXCLUDED.lat, lon = EXCLUDED.lon, "
                      + "source = EXCLUDED.source, resolved_at = EXCLUDED.resolved_at",
                    k, lat, lon, source, Timestamp.valueOf(LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("[Weather] geo_cache upis nije uspeo za '{}': {}", k, e.toString());
        }
    }

    /** Za testove i dijagnostiku. */
    int velicinaMemorije() {
        return memorija.size();
    }
}
