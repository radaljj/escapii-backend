package com.escapii.config;

import com.escapii.service.AppErrorService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Primenjuje {@code db/schema.sql} pri startu, pre nego što server primi prvi zahtev.
 *
 * <p>Produkcija ima {@code DDL_AUTO=none}: Hibernate ne dira šemu, pa je do sada svaka nova
 * kolona tražila ručni SQL pre deploya (i jednom oborila upite kad je zakasnila). Skripta
 * sadrži samo idempotentne naredbe ({@code IF [NOT] EXISTS}), pa je bezopasno pokretati je
 * na svakom startu. Lokalno, gde je {@code ddl-auto=update}, redosled u odnosu na Hibernate
 * nije bitan: ko god prvi napravi tabelu, drugi je preskoči.
 *
 * <p>Naredba koja pukne ne obara aplikaciju - loguje se, beleži kao AppError i ide se dalje.
 * Deploy koji obori ceo start ostavio bi sajt bez backenda; ovako pukne samo funkcija koja
 * zavisi od te tabele.
 */
@Slf4j
@Component
public class SchemaBootstrap {

    static final String SKRIPTA = "db/schema.sql";

    private final JdbcTemplate jdbc;
    private final ObjectProvider<AppErrorService> appErrorService;

    public SchemaBootstrap(JdbcTemplate jdbc, ObjectProvider<AppErrorService> appErrorService) {
        this.jdbc = jdbc;
        this.appErrorService = appErrorService;
    }

    @PostConstruct
    void primeni() {
        List<String> naredbe;
        try {
            naredbe = naredbe(new String(new ClassPathResource(SKRIPTA).getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("[Šema] {} se ne može pročitati: {}", SKRIPTA, e.toString());
            zabelezi(e);
            return;
        }
        int ok = 0;
        for (String naredba : naredbe) {
            try {
                jdbc.execute(naredba);
                ok++;
            } catch (Exception e) {
                log.error("[Šema] Naredba nije prošla ({}): {}", skrati(naredba), e.getMessage());
                zabelezi(e);
            }
        }
        log.info("[Šema] {}: izvršeno {}/{} naredbi", SKRIPTA, ok, naredbe.size());
    }

    /**
     * Prvo skida "--" komentare (i razmake na kraju linije), pa tek onda deli po ";" -
     * inače bi tačka-zarez unutar komentara napravila lažnu naredbu.
     */
    static List<String> naredbe(String skripta) {
        StringBuilder cist = new StringBuilder();
        for (String linija : skripta.split("\r?\n")) {
            String bezKomentara = linija.contains("--") ? linija.substring(0, linija.indexOf("--")) : linija;
            if (!bezKomentara.isBlank()) cist.append(bezKomentara.stripTrailing()).append('\n');
        }
        List<String> rezultat = new ArrayList<>();
        for (String deo : cist.toString().split(";")) {
            String naredba = deo.strip();
            if (!naredba.isEmpty()) rezultat.add(naredba);
        }
        return rezultat;
    }

    private static String skrati(String s) {
        String jedanRed = s.replaceAll("\\s+", " ");
        return jedanRed.length() > 80 ? jedanRed.substring(0, 80) + "…" : jedanRed;
    }

    private void zabelezi(Exception e) {
        try {
            AppErrorService svc = appErrorService.getIfAvailable();
            if (svc != null) svc.record("schema-bootstrap", 0, e);
        } catch (Exception ex) {
            log.warn("[Šema] AppError nije zabeležen: {}", ex.toString());
        }
    }
}
