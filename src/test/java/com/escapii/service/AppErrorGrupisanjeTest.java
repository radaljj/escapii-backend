package com.escapii.service;

import com.escapii.model.AppError;
import com.escapii.model.GiftVoucher;
import com.escapii.repository.AppErrorRepository;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.email.impl.GiftVoucherEmailServiceImpl;
import com.escapii.service.impl.AppErrorServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AppError šalje alarm SAMO za prvu pojavu greške, a "ista greška" je par
 * (endpoint, tip izuzetka). Dva nalaza revizije su oba ležala u toj jednoj rečenici.
 *
 * <p><b>Ključ je bio promenljiv.</b> Neuspelo slanje se beležilo kao
 * {@code "EMAIL customer-confirmation ref=ESC-abc123"} - sa referencom rezervacije
 * unutra. Svaka poruka je time izgledala kao nova greška, pa bi ispad provajdera
 * poslao onoliko alarma koliko je poruka palo, kroz istog provajdera koji ne radi.
 * Dnevna kvota je zajednička sa potvrdama kupcima.
 *
 * <p><b>Upis je bio nezaštićen.</b> {@code record()} je {@code @Async} (5 niti) i radi
 * "nađi pa upiši" bez brave i bez unique constraint-a. Dva istovremena ista pada -
 * tipičan slučaj, jedan bag pogodi više ljudi odjednom - napravila bi dva nerešena
 * reda; {@code Optional} bi od tada bacao izuzetak na SVAKI sledeći poziv, a taj
 * izuzetak guta {@code catch} u samom {@code record()}. Praćenje grešaka bi se tiho
 * ugasilo baš za endpoint koji najviše puca.
 */
class AppErrorGrupisanjeTest {

    // ── 1. Ključ mora biti stabilan ───────────────────────────────────────────

    private static void set(Object target, String field, Object val) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, val);
    }

    private GiftVoucher vaucer(long id) {
        GiftVoucher v = new GiftVoucher();
        v.setId(id);
        v.setCode("ESC-TEST-CODE-000" + id);
        v.setAmount(BigDecimal.valueOf(100));
        v.setBuyerEmail("kupac@example.com");
        return v;
    }

    @Test
    void dvaRazlicitaVauceraDajuISTIkljucGreske() throws Exception {
        EmailSender pada = mock(EmailSender.class);
        when(pada.sendWithAttachment(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(false);
        AppErrorService appError = mock(AppErrorService.class);

        var svc = new GiftVoucherEmailServiceImpl(pada);
        set(svc, "appErrorService", appError);
        set(svc, "frontendUrl", "https://escapii.rs");
        set(svc, "contactEmail", "info@escapii.rs");

        svc.sendVoucherPdfToBuyer(vaucer(11L), new byte[]{1});
        svc.sendVoucherPdfToBuyer(vaucer(22L), new byte[]{2});

        ArgumentCaptor<String> kljuc = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Exception> greska = ArgumentCaptor.forClass(Exception.class);
        verify(appError, times(2)).record(kljuc.capture(), eq(0), greska.capture());

        assertEquals(kljuc.getAllValues().get(0), kljuc.getAllValues().get(1),
                "ključ ne sme da zavisi od vaučera - inače grupisanje nikad ne odradi posao");
        assertFalse(kljuc.getAllValues().get(0).contains("11"),
                "id vaučera ne sme biti u ključu");

        // Konkretan podatak i dalje mora negde da postoji - samo ne u ključu.
        assertTrue(greska.getAllValues().get(1).getMessage().contains("22"),
                "poslednja pogođena stavka mora ostati vidljiva u poruci");
    }

    // ── 2. Upis preživljava duplikate i ne šalje mejl za ponavljanje ──────────

    private AppErrorServiceImpl servis(AppErrorRepository repo, EmailSender sender) throws Exception {
        AppErrorServiceImpl s = new AppErrorServiceImpl(repo, sender);
        set(s, "opsEmail", "ops@escapii.rs");
        return s;
    }

    private AppError red(long id, int count) {
        AppError e = new AppError();
        e.setId(id);
        e.setEndpoint("POST /api/booking");
        e.setExceptionType("IllegalStateException");
        e.setCount(count);
        e.setFirstSeenAt(LocalDateTime.now().minusHours(2));
        e.setLastSeenAt(LocalDateTime.now().minusHours(1));
        return e;
    }

    @Test
    void novaGreskaSeUpisujeIAlarmira() throws Exception {
        AppErrorRepository repo = mock(AppErrorRepository.class);
        EmailSender sender = mock(EmailSender.class);
        when(repo.findByEndpointAndExceptionTypeAndResolvedFalseOrderByIdAsc(anyString(), anyString()))
                .thenReturn(List.of());
        when(repo.save(any(AppError.class))).thenAnswer(inv -> inv.getArgument(0));

        servis(repo, sender).record("POST /api/booking", 500, new IllegalStateException("puklo"));

        verify(repo).save(any(AppError.class));
        verify(sender).send(eq("ops@escapii.rs"), anyString(), anyString());
    }

    @Test
    void ponavljanjeSamoPovecavaBrojacBezMejla() throws Exception {
        AppErrorRepository repo = mock(AppErrorRepository.class);
        EmailSender sender = mock(EmailSender.class);
        when(repo.findByEndpointAndExceptionTypeAndResolvedFalseOrderByIdAsc(anyString(), anyString()))
                .thenReturn(List.of(red(5L, 3)));

        servis(repo, sender).record("POST /api/booking", 500, new IllegalStateException("opet"));

        verify(repo).zabeleziPonavljanje(eq(5L), any(LocalDateTime.class), contains("opet"));
        verify(repo, never()).save(any(AppError.class));
        verifyNoInteractions(sender);
    }

    /**
     * Zaostatak iz ranije verzije: dva nerešena reda za isti par. Ne sme ni da pukne
     * ni da napravi treći - samo nastavi da broji na najstarijem.
     */
    @Test
    void dvaZaostalaRedaZaIstiParNeObaraNistaviseBelezenje() throws Exception {
        AppErrorRepository repo = mock(AppErrorRepository.class);
        EmailSender sender = mock(EmailSender.class);
        when(repo.findByEndpointAndExceptionTypeAndResolvedFalseOrderByIdAsc(anyString(), anyString()))
                .thenReturn(List.of(red(5L, 3), red(9L, 1)));

        assertDoesNotThrow(() ->
                servis(repo, sender).record("POST /api/booking", 500, new IllegalStateException("treci put")));

        verify(repo).zabeleziPonavljanje(eq(5L), any(LocalDateTime.class), anyString());
        verify(repo, never()).save(any(AppError.class));
        verifyNoInteractions(sender);
    }

    /** Sam beležnik grešaka ne sme da obori zahtev koji je već pao. */
    @Test
    void padUBaziNeIzbijaIzRecord() throws Exception {
        AppErrorRepository repo = mock(AppErrorRepository.class);
        EmailSender sender = mock(EmailSender.class);
        when(repo.findByEndpointAndExceptionTypeAndResolvedFalseOrderByIdAsc(anyString(), anyString()))
                .thenThrow(new RuntimeException("baza nedostupna"));

        assertDoesNotThrow(() ->
                servis(repo, sender).record("POST /api/booking", 500, new IllegalStateException("x")));
    }
}
