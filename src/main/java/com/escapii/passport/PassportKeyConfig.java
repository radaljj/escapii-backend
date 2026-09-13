package com.escapii.passport;

import com.escapii.service.AppErrorService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Učitava opcioni {@code PASSPORT_KEY} u {@link PassportCrypto} pre nego što Hibernate
 * išta pročita. Neispravan ključ ne obara aplikaciju: ostaje ugrađeni v1 i stiže AppError.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassportKeyConfig {

    private final AppErrorService appErrorService;

    @Value("${app.passport.key:}")
    private String envKey;

    @PostConstruct
    void configure() {
        try {
            PassportCrypto.configure(envKey);
            boolean v2 = PassportCrypto.V2.equals(PassportCrypto.writeVersion());
            log.info("[Pasoši] Šifrovanje brojeva pasoša: verzija {} ({})", PassportCrypto.writeVersion(),
                    v2 ? "ključ iz PASSPORT_KEY" : "ugrađeni ključ - PASSPORT_KEY nije postavljen");
        } catch (IllegalArgumentException e) {
            PassportCrypto.configure("");
            log.error("[Pasoši] PASSPORT_KEY nije ispravan, ostaje ugrađeni ključ v1: {}", e.getMessage());
            try {
                appErrorService.record("passport-key", 0, e);
            } catch (Exception zabelezi) {
                log.warn("[Pasoši] AppError nije zabeležen: {}", zabelezi.toString());
            }
        }
    }
}
