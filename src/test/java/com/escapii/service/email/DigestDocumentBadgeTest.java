package com.escapii.service.email;

import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.email.impl.DigestEmailServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Jutarnji pregled: rezervacija kojoj dokument nije otišao dobija "Uploaduj dokument" samo kad
 * PDF stvarno fali. Kad je PDF već uploadovan, problem je slanje koje pada - sa "Uploaduj" bi
 * tim ponovo kačio isti fajl umesto da pogleda zašto mejl ne izlazi.
 */
class DigestDocumentBadgeTest {

    private static final LocalDate DANAS = LocalDate.of(2026, 9, 20);

    private static Booking rezervacija(long id, String ref, byte[] pdf) {
        AvailableDate termin = new AvailableDate();
        termin.setDepartureDate(DANAS.plusDays(2));
        termin.setReturnDate(DANAS.plusDays(5));
        Booking b = new Booking();
        b.setId(id);
        b.setBookingRef(ref);
        b.setFirstName("Ana");
        b.setLastName("Anić");
        b.setEmail("ana@primer.rs");
        b.setDepartureAirport("BEG");
        b.setSelectedDate(termin);
        b.setRevealSentAt(LocalDateTime.of(2026, 9, 19, 10, 0));
        b.setForecastSentAt(LocalDateTime.of(2026, 9, 14, 10, 0));
        b.setConfirmationDocument(pdf);
        return b;
    }

    private static String html(List<Booking> upcoming, List<Booking> cekajuDokument) {
        EmailSender sender = mock(EmailSender.class);
        DigestEmailServiceImpl digest = new DigestEmailServiceImpl(sender);
        ReflectionTestUtils.setField(digest, "opsEmail", "tim@primer.rs");

        digest.sendDailyDigest(DANAS, List.of(), List.of(), upcoming, List.of(), cekajuDokument, List.of(), List.of());

        ArgumentCaptor<String> telo = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("tim@primer.rs"), anyString(), telo.capture());
        return telo.getValue();
    }

    @Test
    void bezPdfa_pise_uploadujDokument() {
        Booking b = rezervacija(1L, "ESC-dig00001", null);
        String html = html(List.of(b), List.of(b));
        assertTrue(html.contains("Uploaduj dokument"));
        assertFalse(html.contains("Dokument nije poslat"));
    }

    @Test
    void pdfPostojiASlanjeNijeProslo_pise_dokumentNijePoslat() {
        Booking b = rezervacija(2L, "ESC-dig00002", new byte[]{1, 2, 3});
        String html = html(List.of(b), List.of(b));
        assertTrue(html.contains("Dokument nije poslat"));
        assertFalse(html.contains("Uploaduj dokument"), "PDF je već tu - ne tražimo ponovni upload");
    }

    @Test
    void rezervacijaKojaNeCekaDokument_nemaNijednuOdTeDveOznake() {
        Booking b = rezervacija(3L, "ESC-dig00003", new byte[]{1});
        String html = html(List.of(b), List.of());
        assertFalse(html.contains("Dokument nije poslat"));
        assertFalse(html.contains("Uploaduj dokument"));
    }
}
