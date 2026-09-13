package com.escapii.passport;

import com.escapii.service.AppErrorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Na startu priprema kolonu za šifrat i prešifruje zastarele redove.
 *
 * <ol>
 *   <li>Kolona {@code booking_passengers.passport_serial_number} je bila VARCHAR(50), a šifrat
 *       je duži. Proširenje na VARCHAR(255) je idempotentno i u Postgresu samo metapodatak
 *       (bez prepisivanja tabele), pa ga aplikacija radi sama - bez SQL-a pre deploya i bez
 *       rizika da rezervacije padnu jer je kolona uska. Tek kad proširenje uspe, uključuje
 *       se šifrovanje ({@link PassportCrypto#setEnabled}); inače sve ostaje kao pre.</li>
 *   <li>Svaki zapis koji nije šifrovan aktuelnom verzijom (otvoren tekst iz starih
 *       rezervacija, ili v1 posle uvođenja PASSPORT_KEY) se dešifruje i upiše ponovo.
 *       Radi se direktno SQL-om jer Hibernate ne bi primetio izmenu: vrednost atributa
 *       (otvoren broj) se ne menja, menja se samo oblik u koloni.</li>
 * </ol>
 * Nijedna greška ovde ne obara aplikaciju - loguje se i stiže AppError.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassportStorageInitializer {

    static final String TABLE  = "booking_passengers";
    static final String COLUMN = "passport_serial_number";
    static final int    MIN_LENGTH = 255;

    private final JdbcTemplate    jdbc;
    private final AppErrorService appErrorService;

    @EventListener(ApplicationReadyEvent.class)
    public void naStartu() {
        try {
            if (osigurajSirinuKolone()) {
                PassportCrypto.setEnabled(true);
                presifrujZastarele();
            } else {
                log.warn("[Pasoši] Šifrovanje ostaje ISKLJUČENO - brojevi pasoša se upisuju kao i do sada");
            }
        } catch (Exception e) {
            log.error("[Pasoši] Priprema šifrovanja nije uspela - brojevi ostaju u otvorenom obliku: {}", e.toString(), e);
            zabelezi("passport-storage-init", e);
        }
    }

    /** null = kolona ne postoji; -1 = bez ograničenja dužine (TEXT); inače VARCHAR(n). */
    Integer trenutnaDuzina() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT data_type, character_maximum_length FROM information_schema.columns "
            + "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?", TABLE, COLUMN);
        if (rows.isEmpty()) return null;
        Object max = rows.get(0).get("character_maximum_length");
        return max == null ? -1 : ((Number) max).intValue();
    }

    /** true kad je kolona dovoljno široka za šifrat (po potrebi je proširi). */
    boolean osigurajSirinuKolone() {
        Integer duzina = trenutnaDuzina();
        if (duzina == null) {
            log.warn("[Pasoši] Kolona {}.{} ne postoji", TABLE, COLUMN);
            return false;
        }
        if (duzina == -1 || duzina >= MIN_LENGTH) return true;

        jdbc.execute("ALTER TABLE " + TABLE + " ALTER COLUMN " + COLUMN + " TYPE VARCHAR(" + MIN_LENGTH + ")");
        Integer posle = trenutnaDuzina();
        boolean ok = posle != null && (posle == -1 || posle >= MIN_LENGTH);
        log.info("[Pasoši] Kolona {}.{} proširena sa VARCHAR({}) na VARCHAR({}): {}", TABLE, COLUMN, duzina, MIN_LENGTH, ok ? "OK" : "NIJE USPELO");
        return ok;
    }

    /** Vraća broj prešifrovanih redova. */
    int presifrujZastarele() {
        List<String> vrednosti = jdbc.queryForList(
            "SELECT DISTINCT " + COLUMN + " FROM " + TABLE + " WHERE " + COLUMN + " IS NOT NULL", String.class);
        int presifrovano = 0;
        int necitljivo = 0;
        for (String stored : vrednosti) {
            if (PassportCrypto.isCurrent(stored)) continue;
            String plain;
            try {
                plain = PassportCrypto.decrypt(stored);
            } catch (IllegalStateException e) {
                necitljivo++;
                continue;
            }
            presifrovano += jdbc.update(
                "UPDATE " + TABLE + " SET " + COLUMN + " = ? WHERE " + COLUMN + " = ?",
                PassportCrypto.encrypt(plain), stored);
        }
        if (presifrovano > 0) {
            log.info("[Pasoši] Prešifrovano {} redova na verziju {}", presifrovano, PassportCrypto.writeVersion());
        }
        if (necitljivo > 0) {
            IllegalStateException e = new IllegalStateException(necitljivo
                + " različitih brojeva pasoša je šifrovano verzijom za koju nema ključa - vrati PASSPORT_KEY sa kojim su upisani");
            log.error("[Pasoši] {}", e.getMessage());
            zabelezi("passport-reencrypt", e);
        }
        return presifrovano;
    }

    private void zabelezi(String kontekst, Exception e) {
        try {
            appErrorService.record(kontekst, 0, e);
        } catch (Exception ex) {
            log.warn("[Pasoši] AppError nije zabeležen: {}", ex.toString());
        }
    }
}
