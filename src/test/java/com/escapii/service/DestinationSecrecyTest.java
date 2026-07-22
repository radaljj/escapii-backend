package com.escapii.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Destinacija ne sme da izađe pre reveala (T-2). To je jedino obećanje
 * platforme, pa ovaj test čuva sve puteve kojima bi mogla da procuri.
 *
 * Testovi čitaju izvorni kod jer štite STRUKTURU - da neko kasnije ne doda
 * destinaciju u javni DTO ili ne skine neku od brana.
 */
class DestinationSecrecyTest {

    private static String src(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }

    /** Javni status endpoint sme da vrati status i datume - nikad destinaciju. */
    @Test
    void javniStatusNeVracaDestinaciju() throws Exception {
        String dto = src("src/main/java/com/escapii/dto/BookingStatusResponse.java");
        assertFalse(dto.toLowerCase().contains("destination"),
                "BookingStatusResponse je javan (rate-limited, bez autentifikacije) - "
                + "destinacija u njemu bi je otkrila svakome ko zna broj rezervacije");
    }

    /** revealToken ne sme izaći ni u jednom odgovoru API-ja. */
    @Test
    void revealTokenNeIzlaziKrozApi() throws Exception {
        try (var files = Files.walk(Path.of("src/main/java/com/escapii/dto"))) {
            var leaks = files.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> {
                        try { return src(f.toString()).contains("revealToken"); }
                        catch (Exception e) { return false; }
                    })
                    .map(Path::getFileName)
                    .toList();
            assertTrue(leaks.isEmpty(), "revealToken procureo u DTO: " + leaks);
        }
    }

    /**
     * Prognoza stiže pre reveala, pa ne sme znati destinaciju. Brani se time
     * što je buildHtml uopšte ne prima kao parametar.
     */
    @Test
    void prognozaNeDobijaDestinaciju() throws Exception {
        String s = src("src/main/java/com/escapii/service/email/impl/ForecastEmailServiceImpl.java");

        int start = s.indexOf("private String buildHtml");
        int end   = s.indexOf("{", s.indexOf(")", start));
        String signature = s.substring(start, end);
        assertFalse(signature.contains("estination"),
                "buildHtml ne sme primati destinaciju - prognoza ide pre reveala");

        int subj = s.indexOf("String subject");
        String subjectLine = s.substring(subj, s.indexOf(";", subj));
        assertFalse(subjectLine.contains("Destination") || subjectLine.contains("estination"),
                "naslov prognoze ne sme sadržati destinaciju");
    }

    /** Reveal stranica mora odbiti dok reveal nije stvarno poslat. */
    @Test
    void revealStranicaTraziDaJeRevealPoslat() throws Exception {
        String s = src("src/main/java/com/escapii/service/impl/RevealServiceImpl.java");
        assertTrue(s.contains("getRevealSentAt() == null"),
                "bez ove provere validan token bi otkrio destinaciju pre T-2");
    }

    /**
     * Dokument rezervacije nosi destinaciju u naslovu mejla. Ni automatsko ni
     * ručno slanje ne sme ići pre reveala.
     */
    @Test
    void dokumentRezervacijeCekaReveal() throws Exception {
        String s = src("src/main/java/com/escapii/service/impl/AdminServiceImpl.java");

        int m = s.indexOf("public AdminBookingResponse resendConfirmationDocument");
        assertTrue(m > 0, "metoda nije pronađena");
        String body = s.substring(m, s.indexOf("\n    }", m));
        assertTrue(body.contains("getRevealSentAt() == null"),
                "ručno slanje dokumenta mora odbiti pre reveala - mejl nosi destinaciju u naslovu");
    }
}
