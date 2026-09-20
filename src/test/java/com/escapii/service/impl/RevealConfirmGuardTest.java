package com.escapii.service.impl;

import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.RevealEvent;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.RevealEventRepository;
import com.escapii.service.email.ConfirmationDocumentEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * POST /api/reveal/confirm ("ogrebano"): važi samo za reveal koji je zvanično otključan - iste
 * provere kao GET. Token postoji čim admin unese destinaciju, pa bez ovoga onaj ko ima token
 * može da okine mejl sa dokumentom (koji nosi destinaciju) pre samog reveala.
 */
class RevealConfirmGuardTest {

    private static final String TOKEN = "tok-guard-0001";

    private final BookingRepository rezervacije = mock(BookingRepository.class);
    private final RevealEventRepository dogadjaji = mock(RevealEventRepository.class);
    private final ConfirmationDocumentEmailService mejl = mock(ConfirmationDocumentEmailService.class);
    private final TravelAddonsService dodaci = mock(TravelAddonsService.class);
    private final ConfirmationDocumentAutoSender autoSender = mock(ConfirmationDocumentAutoSender.class);
    private RevealServiceImpl servis;
    private Booking b;

    @BeforeEach
    void setUp() {
        servis = new RevealServiceImpl(rezervacije, dogadjaji, mejl, dodaci, autoSender);
        AvailableDate termin = new AvailableDate();
        termin.setDepartureDate(LocalDate.now().plusDays(2));
        termin.setReturnDate(LocalDate.now().plusDays(5));
        b = new Booking();
        b.setId(7L);
        b.setBookingRef("ESC-guard001");
        b.setStatus(BookingStatus.CONFIRMED);
        b.setSelectedDate(termin);
        b.setAssignedDestination("Prag");
        b.setRevealToken(TOKEN);
        b.setRevealSentAt(LocalDateTime.now().minusHours(1));
        b.setConfirmationDocument(new byte[]{1, 2, 3});
        when(rezervacije.findByRevealToken(TOKEN)).thenReturn(Optional.of(b));
        when(dogadjaji.findByBookingRef("ESC-guard001")).thenReturn(Optional.empty());
    }

    private void nistaSeNijeDesilo() {
        verify(dogadjaji, never()).save(any(RevealEvent.class));
        verifyNoInteractions(mejl);
        assertNull(b.getConfirmationSentAt());
    }

    @Test
    void otkljucanRevealSaDokumentom_beleziDogadjajISaljeDokument() {
        when(mejl.sendConfirmationDocument(b)).thenReturn(true);

        servis.confirmRevealed(TOKEN);

        verify(dogadjaji).save(any(RevealEvent.class));
        verify(mejl).sendConfirmationDocument(b);
        assertNotNull(b.getConfirmationSentAt());
        verify(autoSender, never()).prijaviPadSlanja(any());
    }

    @Test
    void revealJosNijePoslat_javljanjeSeOdbija() {
        b.setRevealSentAt(null);
        servis.confirmRevealed(TOKEN);
        nistaSeNijeDesilo();
    }

    @Test
    void destinacijaNijeUneta_javljanjeSeOdbija() {
        b.setAssignedDestination("  ");
        servis.confirmRevealed(TOKEN);
        nistaSeNijeDesilo();
    }

    @Test
    void polazakJeProsao_javljanjeSeOdbija() {
        b.getSelectedDate().setDepartureDate(LocalDate.now().minusDays(1));
        servis.confirmRevealed(TOKEN);
        nistaSeNijeDesilo();
    }

    @Test
    void naDanPolaskaJosVazi_kaoIGet() {
        b.getSelectedDate().setDepartureDate(LocalDate.now());
        when(mejl.sendConfirmationDocument(b)).thenReturn(true);
        servis.confirmRevealed(TOKEN);
        verify(dogadjaji).save(any(RevealEvent.class));
    }

    @Test
    void rezervacijaNijePotvrdjena_javljanjeSeOdbija() {
        b.setStatus(BookingStatus.CANCELLED);
        servis.confirmRevealed(TOKEN);
        nistaSeNijeDesilo();
    }

    @Test
    void nepoznatToken_neBacaINeRadiNista() {
        when(rezervacije.findByRevealToken("nema")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> servis.confirmRevealed("nema"));
        verify(dogadjaji, never()).save(any(RevealEvent.class));
    }

    @Test
    void drugiPoziv_jeBezEfekta_dokumentNeIdeDvaput() {
        when(dogadjaji.findByBookingRef("ESC-guard001")).thenReturn(Optional.of(new RevealEvent("ESC-guard001")));
        servis.confirmRevealed(TOKEN);
        nistaSeNijeDesilo();
    }

    @Test
    void slanjePadne_dogadjajOstaje_flagPrazan_iTimDobijaPrijavu() {
        when(mejl.sendConfirmationDocument(b)).thenReturn(false);

        servis.confirmRevealed(TOKEN);

        verify(dogadjaji).save(any(RevealEvent.class));   // kupac JESTE video destinaciju
        assertNull(b.getConfirmationSentAt(), "krug mora da ga pokupi i ponovi");
        verify(autoSender).prijaviPadSlanja(b);
    }

    @Test
    void bezDokumenta_samoSeBeleziDogadjaj() {
        b.setConfirmationDocument(null);
        servis.confirmRevealed(TOKEN);
        verify(dogadjaji).save(any(RevealEvent.class));
        verifyNoInteractions(mejl);
    }
}
