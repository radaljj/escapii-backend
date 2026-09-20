package com.escapii.service.impl;

import com.escapii.model.AppError;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.RevealEvent;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.RevealEventRepository;
import com.escapii.service.AppErrorService;
import com.escapii.service.email.ConfirmationDocumentEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pad slanja dokumenta ne sme da ostane samo u logu: kupac je video destinaciju, a karte mu ne
 * stižu. Ide u AppError (mejl timu), a prijava se sama zatvara kad prolaz više nema padova, da
 * sledeći pad opet pošalje mejl.
 */
class ConfirmationDocumentAutoSenderTest {

    private final BookingRepository rezervacije = mock(BookingRepository.class);
    private final RevealEventRepository dogadjaji = mock(RevealEventRepository.class);
    private final ConfirmationDocumentEmailService mejl = mock(ConfirmationDocumentEmailService.class);
    private final AppErrorService greske = mock(AppErrorService.class);
    private ConfirmationDocumentAutoSender sender;

    @BeforeEach
    void setUp() {
        sender = new ConfirmationDocumentAutoSender(rezervacije, dogadjaji, mejl, greske);
    }

    private Booking spremna(long id, String ref) {
        AvailableDate termin = new AvailableDate();
        termin.setDepartureDate(LocalDate.of(2026, 10, 9));
        Booking b = new Booking();
        b.setId(id);
        b.setBookingRef(ref);
        b.setSelectedDate(termin);
        b.setHasRevealBox(false);
        b.setConfirmationDocument(new byte[]{1});
        when(dogadjaji.findByBookingRef(ref)).thenReturn(Optional.of(new RevealEvent(ref)));
        return b;
    }

    private static AppError prijava(long id, String mesto, boolean resena) {
        AppError e = new AppError();
        e.setId(id); e.setEndpoint(mesto); e.setResolved(resena);
        return e;
    }

    @Test
    void padSlanja_ideUAppError_saSifromIPolaskom_aFlagSeNeUpisuje() {
        Booking b = spremna(1L, "ESC-doc00001");
        when(mejl.sendConfirmationDocument(b)).thenReturn(false);

        assertFalse(sender.sendIfReadyAndPending(b));

        ArgumentCaptor<Exception> ex = ArgumentCaptor.forClass(Exception.class);
        verify(greske).record(eq("Dokument rezervacije: slanje"), eq(0), ex.capture());
        assertInstanceOf(ConfirmationDocumentAutoSender.DokumentNijePoslat.class, ex.getValue());
        assertTrue(ex.getValue().getMessage().contains("ESC-doc00001"));
        assertTrue(ex.getValue().getMessage().contains("09.10.2026."));
        verify(rezervacije, never()).markConfirmationSent(anyLong(), any());
    }

    @Test
    void uspesnoSlanje_nemaPrijave() {
        Booking b = spremna(1L, "ESC-doc00001");
        when(mejl.sendConfirmationDocument(b)).thenReturn(true);

        assertTrue(sender.sendIfReadyAndPending(b));

        verify(rezervacije).markConfirmationSent(eq(1L), any());
        verify(greske, never()).record(any(), anyInt(), any());
    }

    @Test
    void nijeSpremnoZaSlanje_nijePad_nemaPrijave() {
        Booking bezDogadjaja = spremna(2L, "ESC-doc00002");
        when(dogadjaji.findByBookingRef("ESC-doc00002")).thenReturn(Optional.empty());

        assertFalse(sender.sendIfReadyAndPending(bezDogadjaja));

        verifyNoInteractions(mejl);
        verify(greske, never()).record(any(), anyInt(), any());
    }

    @Test
    void padBelezenjaGreske_neObaraSlanje() {
        Booking b = spremna(1L, "ESC-doc00001");
        when(mejl.sendConfirmationDocument(b)).thenReturn(false);
        doThrow(new IllegalStateException("baza")).when(greske).record(any(), anyInt(), any());

        assertDoesNotThrow(() -> sender.sendIfReadyAndPending(b));
    }

    @Test
    void prolazBezPadova_zatvaraOtvorenePrijaveSamoZaOvoMesto() {
        Booking b = spremna(1L, "ESC-doc00001");
        when(rezervacije.findPendingConfirmationDocuments(any())).thenReturn(List.of(b));
        when(mejl.sendConfirmationDocument(b)).thenReturn(true);
        when(greske.countUnresolved()).thenReturn(2L);
        when(greske.getAll()).thenReturn(List.of(
                prijava(11L, "Dokument rezervacije: slanje", false),
                prijava(12L, "Jutarnji krug: reveal", false),
                prijava(13L, "Dokument rezervacije: slanje", true)));

        assertEquals(1, sender.sendAllPending());

        verify(greske).resolve(11L);
        verify(greske, never()).resolve(12L);
        verify(greske, never()).resolve(13L);
    }

    @Test
    void prazanProlaz_takodjeZatvaraPrijave_dokumentJeOtisaoDrugimPutem() {
        when(rezervacije.findPendingConfirmationDocuments(any())).thenReturn(List.of());
        when(greske.countUnresolved()).thenReturn(1L);
        when(greske.getAll()).thenReturn(List.of(prijava(11L, "Dokument rezervacije: slanje", false)));

        sender.sendAllPending();

        verify(greske).resolve(11L);
    }

    @Test
    void prolazSaPadom_neZatvaraNista() {
        Booking dobra = spremna(1L, "ESC-doc00001");
        Booking losa  = spremna(2L, "ESC-doc00002");
        when(rezervacije.findPendingConfirmationDocuments(any())).thenReturn(List.of(dobra, losa));
        when(mejl.sendConfirmationDocument(dobra)).thenReturn(true);
        when(mejl.sendConfirmationDocument(losa)).thenReturn(false);

        assertEquals(1, sender.sendAllPending());

        verify(greske).record(eq("Dokument rezervacije: slanje"), eq(0), any());
        verify(greske, never()).getAll();
        verify(greske, never()).resolve(anyLong());
    }

    @Test
    void bezOtvorenihGresaka_neCitaCeluListu() {
        when(rezervacije.findPendingConfirmationDocuments(any())).thenReturn(List.of());
        when(greske.countUnresolved()).thenReturn(0L);

        sender.sendAllPending();

        verify(greske, never()).getAll();
    }
}
