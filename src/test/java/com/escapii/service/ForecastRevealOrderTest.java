package com.escapii.service;

import com.escapii.config.DailyTaskScheduler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dva pravila koja lako pukne neko ko kasnije menja raspored:
 *
 *  1. Prognoza se šalje PRE reveala - pisana je kao najava i ne imenuje grad,
 *     pa posle reveala nema smisla.
 *  2. Prognoza se ne propušta - pokušava se svakog dana do polaska.
 *
 * Kod kasno potvrđene rezervacije oba padaju istog dana, i tada je jedino
 * što drži redosled to što ih scheduler zove tim redom, sinhrono.
 */
class ForecastRevealOrderTest {

    /** Granice iz BookingSchedulingServiceImpl - drže se ovde uparene sa kodom. */
    private static LocalDate forecastFrom(LocalDate today)  { return today; }
    private static LocalDate forecastUntil(LocalDate today) { return today.plusDays(7); }
    private static LocalDate revealCutoff(LocalDate today)  { return today.plusDays(2); }

    private static boolean forecastFires(LocalDate today, LocalDate departure) {
        return !departure.isBefore(forecastFrom(today)) && !departure.isAfter(forecastUntil(today));
    }

    private static boolean revealFires(LocalDate today, LocalDate departure) {
        return !departure.isAfter(revealCutoff(today));
    }

    @Test
    void prognozaSePokusavaSvakogDanaDoPolaska() {
        LocalDate departure = LocalDate.of(2026, 7, 24);
        // Od T-7 do dana polaska - nijedan dan ne sme da propadne
        for (int d = 7; d >= 0; d--) {
            LocalDate today = departure.minusDays(d);
            assertTrue(forecastFires(today, departure),
                    "prognoza mora da okine na T-" + d);
        }
    }

    @Test
    void prognozaNeIdePreT7NiPoslePolaska() {
        LocalDate departure = LocalDate.of(2026, 7, 24);
        assertFalse(forecastFires(departure.minusDays(8), departure), "T-8 je prerano");
        assertFalse(forecastFires(departure.plusDays(1), departure), "polazak je prošao");
    }

    /**
     * Ključni scenario: rezervacija potvrđena na T-2. Oba mejla padaju istog
     * dana - prognoza sme, i mora ići prva.
     */
    @Test
    void kadaObaPadnuIstogDanaPrognozaIdePrva() throws Exception {
        LocalDate departure = LocalDate.of(2026, 7, 24);
        LocalDate today     = departure.minusDays(2);

        assertTrue(forecastFires(today, departure), "prognoza mora da okine i na T-2");
        assertTrue(revealFires(today, departure),   "reveal okida na T-2");

        // Redosled poziva u scheduleru je jedino što ih razdvaja
        String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "src/main/java/com/escapii/config/DailyTaskScheduler.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        int forecastAt = src.indexOf("sendPendingForecasts()");
        int revealAt   = src.indexOf("sendPendingReveals()");
        assertTrue(forecastAt > 0 && revealAt > 0, "oba poziva moraju postojati");
        assertTrue(forecastAt < revealAt,
                "sendPendingForecasts() mora biti pozvan PRE sendPendingReveals()");
    }

    /** Slanje mora ostati sinhrono - sa @Async redosled poziva ništa ne znači. */
    @Test
    void lanacSlanjaOstajeSinhron() throws Exception {
        for (String path : new String[]{
                "src/main/java/com/escapii/config/DailyTaskScheduler.java",
                "src/main/java/com/escapii/service/impl/BookingSchedulingServiceImpl.java",
                "src/main/java/com/escapii/service/email/impl/ForecastEmailServiceImpl.java",
                "src/main/java/com/escapii/service/email/impl/RevealEmailServiceImpl.java"}) {
            String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path)),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(src.contains("@Async"),
                    path + " ne sme biti @Async - to bi razbilo redosled prognoza→reveal");
        }
    }

    /** Reveal ima nadoknadu: ako je dan propušten, ide i kasnije. */
    @Test
    void revealSeNadoknadjuje() {
        LocalDate departure = LocalDate.of(2026, 7, 24);
        assertFalse(revealFires(departure.minusDays(3), departure), "T-3 je prerano za reveal");
        assertTrue(revealFires(departure.minusDays(2), departure),  "T-2 je okidač");
        assertTrue(revealFires(departure.minusDays(1), departure),  "T-1 nadoknada");
        assertTrue(revealFires(departure, departure),               "dan polaska, nadoknada");
    }
}
