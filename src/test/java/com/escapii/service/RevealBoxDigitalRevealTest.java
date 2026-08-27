package com.escapii.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reveal Box je fizicki dodatak. Digitalni reveal (mejl sa linkom) ide SVIMA,
 * i sa kutijom i bez nje. Ranije je box preskakan u vise mesta - ovi strukturalni
 * testovi cuvaju da se ta stara logika ne vrati kroz zadnja vrata.
 *
 * Sto ostaje odvojeno: hasRevealBox / revealBoxSent oznacavaju fizicku posiljku,
 * revealSentAt / RevealEvent oznacavaju digitalno otkrivanje.
 */
class RevealBoxDigitalRevealTest {

    private static String src(String p) throws Exception {
        return new String(Files.readAllBytes(Path.of(p)), StandardCharsets.UTF_8);
    }

    /** Auto scheduler mora slati reveal i za box rezervacije. */
    @Test
    void autoRevealNePreskaceBoxRezervacije() throws Exception {
        String s = src("src/main/java/com/escapii/service/impl/BookingSchedulingServiceImpl.java");
        int loop = s.indexOf("private List<Booking> sendReveals");
        assertTrue(loop > 0, "sendReveals nije pronađen");
        int end = s.indexOf("private List<Booking> sendForecasts", loop);
        String body = s.substring(loop, end > loop ? end : s.length());

        assertFalse(body.contains("getHasRevealBox()"),
                "sendReveals ne sme granati po getHasRevealBox - digitalni reveal ide svima");
    }

    /** Rucni endpoint mora primati box rezervacije - ne 409. */
    @Test
    void rucniRevealNijeBlokiranZaBoxRezervacije() throws Exception {
        String s = src("src/main/java/com/escapii/service/impl/BookingSchedulingServiceImpl.java");
        int m = s.indexOf("public Map<String, String> sendRevealForBooking");
        assertTrue(m > 0, "sendRevealForBooking nije pronađen");
        String body = s.substring(m, s.indexOf("\n    }", m));

        assertFalse(body.contains("getHasRevealBox()"),
                "rucni reveal ne sme bacati 409 kad je hasRevealBox=true");
    }

    /** Upiti za viewing/reminder ukljucuju box - i on sad ima RevealEvent tok. */
    @Test
    void repositoryUpitiZaRevealNeIskljucujuBox() throws Exception {
        String s = src("src/main/java/com/escapii/repository/BookingRepository.java");

        // findRevealedAndViewed / findRevealedButNotViewed / findReadyForCompletion
        // nekad su imali "hasRevealBox = false" / "OR hasRevealBox = true" - sve se sklanja.
        int viewed  = s.indexOf("findRevealedAndViewed");
        int notView = s.indexOf("findRevealedButNotViewed");
        int compl   = s.indexOf("findReadyForCompletion");
        assertTrue(viewed > 0 && notView > 0 && compl > 0, "upiti nisu pronađeni");

        // Provera dorucnim opsegom oko svakog upita - ne sme se pominjati hasRevealBox.
        String viewedQuery  = s.substring(viewed  - 400, viewed);
        String notViewQuery = s.substring(notView - 400, notView);
        String complQuery   = s.substring(compl   - 400, compl);

        assertFalse(viewedQuery.contains("hasRevealBox"),
                "findRevealedAndViewed ne sme filtrirati po hasRevealBox");
        assertFalse(notViewQuery.contains("hasRevealBox"),
                "findRevealedButNotViewed ne sme filtrirati po hasRevealBox");
        assertFalse(complQuery.contains("hasRevealBox"),
                "findReadyForCompletion ne sme filtrirati po hasRevealBox - svi cekaju revealSentAt");
    }

    /** Fizicki tok ostaje: markRevealBoxSent i findPendingRevealBoxes su nedirnuti. */
    @Test
    void fizickiTokKutijeOstajeNezavisan() throws Exception {
        String svc = src("src/main/java/com/escapii/service/impl/AdminServiceImpl.java");
        assertTrue(svc.contains("markRevealBoxSent"),
                "admin akcija za oznacavanje poslate kutije mora ostati");

        String repo = src("src/main/java/com/escapii/repository/BookingRepository.java");
        assertTrue(repo.contains("findPendingRevealBoxes"),
                "podsetnik adminu za neposlate kutije mora ostati");
    }

    /**
     * Dokument rezervacije (posle upload-a) mora cekati RevealEvent i za box.
     * Ranije je box zaobilazio proveru; sad je izuzetak uklonjen.
     */
    @Test
    void dokumentRezervacijeCekaRevealIZaBox() throws Exception {
        String s = src("src/main/java/com/escapii/service/impl/AdminServiceImpl.java");

        int upload = s.indexOf("public AdminBookingResponse uploadConfirmationDocument");
        assertTrue(upload > 0, "uploadConfirmationDocument nije pronađen");
        String uploadBody = s.substring(upload, s.indexOf("\n    }", upload));
        assertFalse(uploadBody.contains("getHasRevealBox()"),
                "upload ne sme vise granati po hasRevealBox - box takodje ceka RevealEvent");

        int resend = s.indexOf("public AdminBookingResponse resendConfirmationDocument");
        assertTrue(resend > 0, "resendConfirmationDocument nije pronađen");
        String resendBody = s.substring(resend, s.indexOf("\n    }", resend));
        assertFalse(resendBody.contains("getHasRevealBox()"),
                "resend ne sme vise granati po hasRevealBox - box takodje ceka RevealEvent");
    }
}
