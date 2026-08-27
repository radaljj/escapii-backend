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
     * ručno slanje ne sme ići pre nego sto kupac zna destinaciju.
     *
     * Pravilo (centralizovano u ConfirmationDocumentAutoSender.canReceive):
     *   - non-box: RevealEvent postoji (kupac je kliknuo reveal link)
     *   - box:     revealSentAt postavljen (kutija je vec otkrila destinaciju
     *              na T-5..T-3, digitalni reveal je poslat na T-2)
     */
    @Test
    void dokumentRezervacijeCekaReveal() throws Exception {
        String s = src("src/main/java/com/escapii/service/impl/AdminServiceImpl.java");
        int m = s.indexOf("public AdminBookingResponse resendConfirmationDocument");
        assertTrue(m > 0, "metoda nije pronađena");
        String body = s.substring(m, s.indexOf("\n    }", m));
        assertTrue(body.contains("confirmationDocumentAutoSender.canReceive"),
                "resend mora zvati centralno pravilo (canReceive), a ne inline provere");
        // Napomena: hasRevealBox se sme pominjati u resend body-ju iskljucivo za
        // tekst error poruke (razliciti razlog za box vs non-box), NE kao gard.
        // Gard je iskljucivo canReceive - proverili smo gore.

        // Isto centralno pravilo mora vaziti i za autoSender.
        String as = src("src/main/java/com/escapii/service/impl/ConfirmationDocumentAutoSender.java");
        int cr = as.indexOf("public boolean canReceive");
        assertTrue(cr > 0, "canReceive nije pronađen");
        String crBody = as.substring(cr, as.indexOf("\n    }", cr));
        assertTrue(crBody.contains("getRevealSentAt()"),
                "box grana canReceive mora traziti revealSentAt");
        assertTrue(crBody.contains("revealEventRepository.findByBookingRef"),
                "non-box grana canReceive mora traziti RevealEvent");
    }
}
