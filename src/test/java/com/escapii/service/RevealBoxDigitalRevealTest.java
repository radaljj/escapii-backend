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

    /**
     * findReadyForCompletion cezmi mora ostati centriran samo na revealSentAt -
     * i box i non-box zavrsavaju putovanje isto. Nije mesto za hasRevealBox filter.
     */
    @Test
    void findReadyForCompletionNeFiltriraPoBoxu() throws Exception {
        String s = src("src/main/java/com/escapii/repository/BookingRepository.java");
        int compl = s.indexOf("findReadyForCompletion");
        assertTrue(compl > 0, "findReadyForCompletion nije pronađen");
        String complQuery = s.substring(compl - 400, compl);
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
     * Dokument (upload i resend) ide kroz centralni autoSender - ne sme vise
     * direktno da granata po hasRevealBox u AdminServiceImpl. Pravilo (canReceive)
     * mora biti na jednom mestu da scheduler retry i sinhrono slanje daju isti odgovor.
     */
    @Test
    void adminUsesCentralnaLogiku() throws Exception {
        String s = src("src/main/java/com/escapii/service/impl/AdminServiceImpl.java");

        int upload = s.indexOf("public AdminBookingResponse uploadConfirmationDocument");
        assertTrue(upload > 0, "uploadConfirmationDocument nije pronađen");
        String uploadBody = s.substring(upload, s.indexOf("\n    }", upload));
        assertTrue(uploadBody.contains("confirmationDocumentAutoSender"),
                "upload mora ici kroz autoSender (centralno pravilo canReceive)");

        int resend = s.indexOf("public AdminBookingResponse resendConfirmationDocument");
        assertTrue(resend > 0, "resendConfirmationDocument nije pronađen");
        String resendBody = s.substring(resend, s.indexOf("\n    }", resend));
        assertTrue(resendBody.contains("confirmationDocumentAutoSender.canReceive"),
                "resend mora zvati autoSender.canReceive umesto direktne provere RevealEvent-a");
    }

    /**
     * Scheduler cycle (sendReveals + retry) mora imati dve provere u DailyTaskScheduler-u:
     * hook posle reveala (kroz autoSender u sendReveals) + dnevni retry za pukla slanja.
     * Bez retry-a box korisnik moze "propadnuti kroz mrezu" - reveal poslat, dokument nikad.
     */
    @Test
    void schedulerImaHookIRetry() throws Exception {
        String reveal = src("src/main/java/com/escapii/service/impl/BookingSchedulingServiceImpl.java");
        assertTrue(reveal.contains("confirmationDocumentAutoSender.sendIfReadyAndPending"),
                "sendReveals i sendRevealForBooking moraju pozvati autoSender.sendIfReadyAndPending "
                + "posle setovanja revealSentAt - da box korisnik dobije dokument bez klika");

        String sched = src("src/main/java/com/escapii/config/DailyTaskScheduler.java");
        assertTrue(sched.contains("confirmationDocumentAutoSender.sendAllPending"),
                "DailyTaskScheduler mora zvati sendAllPending za retry - safety net za pukla slanja");
    }

    /**
     * Box korisnik ne treba da bude alarmiran u "nije otvorio reveal" upozorenju:
     * nije duzan da klikne link, dokument mu ide automatski cim reveal krene.
     */
    @Test
    void findRevealedButNotViewedIskljucujeBox() throws Exception {
        String s = src("src/main/java/com/escapii/repository/BookingRepository.java");
        int m = s.indexOf("findRevealedButNotViewed");
        assertTrue(m > 0, "findRevealedButNotViewed nije pronađen");
        String around = s.substring(m - 500, m);
        assertTrue(around.contains("b.hasRevealBox = false"),
                "findRevealedButNotViewed mora eksplicitno iskljuciti box rezervacije - "
                + "one nemaju obavezu klika, ne alarmirati tim ako nije otvorio");
    }
}
