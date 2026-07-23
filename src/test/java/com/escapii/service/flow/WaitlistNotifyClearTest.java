package com.escapii.service.flow;

import com.escapii.model.WaitlistEntry;
import com.escapii.repository.WaitlistRepository;
import com.escapii.service.email.WaitlistEmailService;
import com.escapii.service.impl.WaitlistServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * notifyAndClear() briše ljude sa liste čekanja kad se otvore novi termini.
 * To je nepovratno - nema "pošalji ponovo", pa je jedini ispravan uslov za
 * brisanje "mejl je stvarno otišao".
 *
 * Bag koji je ovo hvatalo: sendWaitlistNotification je bila @Async void i
 * odbacivala boolean iz EmailSender.send(). Petlja je brisala unos ODMAH
 * posle poziva, bez obzira na ishod - komentar u kodu je tvrdio "Only delete
 * entries where the email was successfully sent", što nije bilo istina.
 */
@ExtendWith(MockitoExtension.class)
class WaitlistNotifyClearTest {

    @Mock private WaitlistRepository waitlistRepository;
    @Mock private WaitlistEmailService waitlistEmailService;

    private WaitlistEntry entry(long id, String email) {
        WaitlistEntry e = new WaitlistEntry();
        e.setId(id);
        e.setEmail(email);
        e.setAirport("BEG");
        return e;
    }

    @Test
    void uspesnoSlanjeBrisePreteceUnos() {
        var svc = new WaitlistServiceImpl(waitlistRepository, waitlistEmailService);
        WaitlistEntry e = entry(1L, "putnik@example.com");
        when(waitlistRepository.findByAirportOrderByCreatedAtAsc("BEG")).thenReturn(List.of(e));
        when(waitlistEmailService.sendWaitlistNotification("putnik@example.com", "BEG")).thenReturn(true);

        int sent = svc.notifyAndClear("BEG");

        assertEquals(1, sent);
        verify(waitlistRepository).delete(e);
    }

    /** Srž zaštite: propalo slanje NE sme obrisati unos. */
    @Test
    void propaliSlanjeOstavljaUnosNaListi() {
        var svc = new WaitlistServiceImpl(waitlistRepository, waitlistEmailService);
        WaitlistEntry e = entry(2L, "drugi@example.com");
        when(waitlistRepository.findByAirportOrderByCreatedAtAsc("BEG")).thenReturn(List.of(e));
        when(waitlistEmailService.sendWaitlistNotification("drugi@example.com", "BEG")).thenReturn(false);

        int sent = svc.notifyAndClear("BEG");

        assertEquals(0, sent, "neuspešno slanje ne sme se računati kao poslato");
        verify(waitlistRepository, never()).delete(any());
    }

    /** Mešoviti slučaj - samo uspešni nestaju sa liste, ostali čekaju sledeći pokušaj. */
    @Test
    void mesovitiIshodBrisSamoUspesne() {
        var svc = new WaitlistServiceImpl(waitlistRepository, waitlistEmailService);
        WaitlistEntry ok   = entry(3L, "ok@example.com");
        WaitlistEntry fail = entry(4L, "fail@example.com");
        when(waitlistRepository.findByAirportOrderByCreatedAtAsc("BEG")).thenReturn(List.of(ok, fail));
        when(waitlistEmailService.sendWaitlistNotification("ok@example.com", "BEG")).thenReturn(true);
        when(waitlistEmailService.sendWaitlistNotification("fail@example.com", "BEG")).thenReturn(false);

        int sent = svc.notifyAndClear("BEG");

        assertEquals(1, sent);
        verify(waitlistRepository).delete(ok);
        verify(waitlistRepository, never()).delete(fail);
    }

    /** Izuzetak (npr. NPE pre slanja) ne sme srušiti obradu ostatka liste. */
    @Test
    void izuzetakZaJednogNePrekidaOstale() {
        var svc = new WaitlistServiceImpl(waitlistRepository, waitlistEmailService);
        WaitlistEntry crashes = entry(5L, "crash@example.com");
        WaitlistEntry ok      = entry(6L, "ok2@example.com");
        when(waitlistRepository.findByAirportOrderByCreatedAtAsc("BEG")).thenReturn(List.of(crashes, ok));
        when(waitlistEmailService.sendWaitlistNotification("crash@example.com", "BEG"))
                .thenThrow(new RuntimeException("boom"));
        when(waitlistEmailService.sendWaitlistNotification("ok2@example.com", "BEG")).thenReturn(true);

        int sent = svc.notifyAndClear("BEG");

        assertEquals(1, sent);
        verify(waitlistRepository, never()).delete(crashes);
        verify(waitlistRepository).delete(ok);
    }
}
