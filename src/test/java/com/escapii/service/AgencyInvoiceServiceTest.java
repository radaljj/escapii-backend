package com.escapii.service;

import com.escapii.dto.AgencyInvoicePreview;
import com.escapii.dto.AgencyInvoiceResponse;
import com.escapii.dto.AgencySettlementResponse;
import com.escapii.model.*;
import com.escapii.repository.AgencyInvoiceRepository;
import com.escapii.repository.AgencyInvoiceSequenceRepository;
import com.escapii.repository.AgencyRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.service.email.InvoiceEmailService;
import com.escapii.service.impl.AgencyInvoiceServiceImpl;
import com.escapii.service.invoice.AgencyInvoiceData;
import com.escapii.service.invoice.InvoicePdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Zbirna faktura agenciji: ulaze samo završena putovanja sa unetim troškovima, iznos je
 * zbir Escapii zarade (vaučer se ne odbija), mejl ide pre upisa, storno vraća rezervacije u red.
 */
class AgencyInvoiceServiceTest {

    private final AgencyRepository agencije = mock(AgencyRepository.class);
    private final BookingRepository rezervacije = mock(BookingRepository.class);
    private final AgencyInvoiceRepository fakture = mock(AgencyInvoiceRepository.class);
    private final AgencyInvoiceSequenceRepository sekvenca = mock(AgencyInvoiceSequenceRepository.class);
    private final AgencySettlementCalculator kalkulator = mock(AgencySettlementCalculator.class);
    private final InvoicePdfService pdf = mock(InvoicePdfService.class);
    private final InvoiceEmailService mejl = mock(InvoiceEmailService.class);

    private AgencyInvoiceServiceImpl svc;
    private Agency sani;

    @BeforeEach
    void setUp() {
        svc = new AgencyInvoiceServiceImpl(agencije, rezervacije, fakture, sekvenca, kalkulator, pdf, mejl);
        ReflectionTestUtils.setField(svc, "dueDays", 8);
        ReflectionTestUtils.setField(svc, "companyName", "Escapii d.o.o.");
        sani = new Agency();
        sani.setId(3L);
        sani.setName("Sani Tours");
        sani.setContactName("Sandra");
        sani.setContactEmail("sandra@sani.rs");
        when(agencije.findById(3L)).thenReturn(Optional.of(sani));
        when(agencije.findByIdForUpdate(3L)).thenReturn(Optional.of(sani));
        when(rezervacije.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fakture.save(any(AgencyInvoice.class))).thenAnswer(inv -> {
            AgencyInvoice i = inv.getArgument(0);
            if (i.getId() == null) i.setId(9L);
            return i;
        });
        when(pdf.generateAgency(any(AgencyInvoiceData.class))).thenReturn("%PDF".getBytes());
        when(mejl.sendAgencyInvoice(any(), any())).thenReturn(true);
        when(sekvenca.findByYear(anyInt())).thenAnswer(inv -> {
            AgencyInvoiceSequence s = new AgencyInvoiceSequence(inv.getArgument(0));
            s.setLastSeq(6);
            return Optional.of(s);
        });
    }

    private static Booking booking(long id, String ref, LocalDate povratak) {
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(povratak.minusDays(3));
        d.setReturnDate(povratak);
        Booking b = new Booking();
        b.setId(id);
        b.setBookingRef(ref);
        b.setStatus(BookingStatus.COMPLETED);
        b.setSettlementStatus(SettlementStatus.READY_FOR_INVOICE);
        b.setAgencyIdSnapshot(3L);
        b.setAgencyNameSnapshot("Sani Tours");
        b.setNumberOfTravelers(2);
        b.setSelectedDate(d);
        return b;
    }

    private static AgencySettlementResponse spremno(String zarada, String vaucer) {
        BigDecimal z = new BigDecimal(zarada);
        BigDecimal v = new BigDecimal(vaucer);
        return AgencySettlementResponse.builder()
                .readyForInvoice(true).validationErrors(List.of())
                .escapiiEarnings(z).voucherApplied(v).netSettlement(z.subtract(v))
                .build();
    }

    private static AgencySettlementResponse nijeSpremno(String razlog) {
        return AgencySettlementResponse.builder()
                .readyForInvoice(false).validationErrors(List.of(razlog))
                .escapiiEarnings(BigDecimal.ZERO)
                .build();
    }

    @Test
    void pregled_ukljucujeSamoSpremne_sabiraZaradu_vaucerSeNeOdbija() {
        Booking a = booking(1, "ESC-aaaa1111", LocalDate.of(2026, 9, 5));
        Booking b = booking(2, "ESC-bbbb2222", LocalDate.of(2026, 9, 12));
        Booking c = booking(3, "ESC-cccc3333", LocalDate.of(2026, 9, 9));
        when(rezervacije.findCompletedNotInvoiced(3L)).thenReturn(List.of(a, c, b));
        when(kalkulator.calculate(a)).thenReturn(spremno("69.50", "20.00"));
        when(kalkulator.calculate(b)).thenReturn(spremno("30.00", "0.00"));
        when(kalkulator.calculate(c)).thenReturn(nijeSpremno("Nedostaju troskovi agencije za neke 50/50 stavke - obracun nije spreman za fakturu."));
        when(rezervacije.countByAgencyIdSnapshotAndStatusAndAgencyInvoiceIsNull(3L, BookingStatus.CONFIRMED)).thenReturn(4L);

        AgencyInvoicePreview p = svc.preview(3L);

        assertEquals(new BigDecimal("99.50"), p.amount(), "69,50 + 30,00 - vaučer od 20€ se NE odbija");
        assertEquals(2, p.bookingCount());
        assertEquals(List.of("ESC-aaaa1111", "ESC-bbbb2222"), p.included().stream().map(AgencyInvoicePreview.Line::bookingRef).toList());
        assertEquals(1, p.needsCosts().size());
        assertEquals("ESC-cccc3333", p.needsCosts().get(0).bookingRef());
        assertTrue(p.needsCosts().get(0).reason().contains("troskovi"));
        assertEquals(4, p.inProgress());
        // period = najraniji POLAZAK (a: 02.09.) -> najkasniji POVRATAK (b: 12.09.)
        assertEquals(LocalDate.of(2026, 9, 2), p.periodFrom());
        assertEquals(LocalDate.of(2026, 9, 12), p.periodTo());
        assertEquals("Marketinške usluge za period 02.09.2026. – 12.09.2026.", p.suggestedDescription());
        assertTrue(p.canInvoice());
        assertNull(p.blocker());
        assertEquals("sandra@sani.rs", p.agencyEmail());
    }

    @Test
    void pregled_bezMejlaAgencije_blokira() {
        sani.setContactEmail("  ");
        Booking a = booking(1, "ESC-aaaa1111", LocalDate.of(2026, 9, 5));
        when(rezervacije.findCompletedNotInvoiced(3L)).thenReturn(List.of(a));
        when(kalkulator.calculate(a)).thenReturn(spremno("50.00", "0.00"));

        AgencyInvoicePreview p = svc.preview(3L);

        assertFalse(p.canInvoice());
        assertTrue(p.blocker().contains("mejl"), p.blocker());
        assertEquals(new BigDecimal("50.00"), p.amount(), "iznos se i dalje pokazuje");
    }

    @Test
    void pregled_bezZavrsenih_blokira_aSamoBezTroskova_kazeDaSeUnesu() {
        when(rezervacije.findCompletedNotInvoiced(3L)).thenReturn(List.of());
        AgencyInvoicePreview prazno = svc.preview(3L);
        assertFalse(prazno.canInvoice());
        assertTrue(prazno.blocker().contains("Nema završenih"), prazno.blocker());

        Booking c = booking(3, "ESC-cccc3333", LocalDate.of(2026, 9, 9));
        when(rezervacije.findCompletedNotInvoiced(3L)).thenReturn(List.of(c));
        when(kalkulator.calculate(c)).thenReturn(nijeSpremno("Nedostaju troskovi"));
        AgencyInvoicePreview bezTroskova = svc.preview(3L);
        assertFalse(bezTroskova.canInvoice());
        assertTrue(bezTroskova.blocker().contains("troškove"), bezTroskova.blocker());
    }

    @Test
    void create_dodeljujeBroj_saljeMejl_cuvaFakturu_iZakljucavaRezervacije() {
        Booking a = booking(1, "ESC-aaaa1111", LocalDate.of(2026, 9, 5));
        Booking b = booking(2, "ESC-bbbb2222", LocalDate.of(2026, 9, 12));
        when(rezervacije.findCompletedNotInvoiced(3L)).thenReturn(List.of(a, b));
        when(kalkulator.calculate(a)).thenReturn(spremno("69.50", "20.00"));
        when(kalkulator.calculate(b)).thenReturn(spremno("30.00", "0.00"));

        AgencyInvoiceResponse r = svc.create(3L, "  Marketinške usluge za septembar  ");

        int godina = LocalDate.now().getYear();
        assertEquals("ESC-AG-" + godina + "-0007", r.invoiceNumber());
        assertEquals(new BigDecimal("99.50"), r.amount());
        assertEquals(2, r.bookingCount());
        assertEquals("Marketinške usluge za septembar", r.description(), "opis se trimuje");
        assertEquals(AgencyInvoiceStatus.SENT, r.status());
        assertEquals(LocalDate.now(), r.issuedAt());
        assertEquals(LocalDate.now().plusDays(8), r.dueDate());
        assertNotNull(r.sentAt());
        assertEquals(List.of("ESC-aaaa1111", "ESC-bbbb2222"), r.bookingRefs());

        // rezervacije zaključane i vezane za fakturu
        for (Booking x : List.of(a, b)) {
            assertEquals(SettlementStatus.INVOICED, x.getSettlementStatus());
            assertNotNull(x.getAgencyInvoicedAt());
            assertNotNull(x.getAgencyInvoice());
            assertEquals("ESC-AG-" + godina + "-0007", x.invoiceNumberForDisplay());
        }
        // PDF sa pravim podacima, mejl na agenciju, sekvenca uvećana i sačuvana
        ArgumentCaptor<AgencyInvoiceData> data = ArgumentCaptor.forClass(AgencyInvoiceData.class);
        verify(pdf).generateAgency(data.capture());
        assertEquals("Sani Tours", data.getValue().agencyName());
        assertEquals("Sandra", data.getValue().agencyContact());
        assertEquals("99,50", data.getValue().amountFormatted());
        ArgumentCaptor<AgencyInvoice> poslata = ArgumentCaptor.forClass(AgencyInvoice.class);
        verify(mejl).sendAgencyInvoice(poslata.capture(), eq("%PDF".getBytes()));
        assertEquals("sandra@sani.rs", poslata.getValue().getAgencyEmail());
        verify(sekvenca).ensureYearRow(godina);
        verify(sekvenca).save(argThat(s -> s.getLastSeq() == 7));
        verify(agencije).findByIdForUpdate(3L);
    }

    @Test
    void create_kadMejlNeOde_vraca502_iNistaSeNeCuva() {
        Booking a = booking(1, "ESC-aaaa1111", LocalDate.of(2026, 9, 5));
        when(rezervacije.findCompletedNotInvoiced(3L)).thenReturn(List.of(a));
        when(kalkulator.calculate(a)).thenReturn(spremno("50.00", "0.00"));
        when(mejl.sendAgencyInvoice(any(), any())).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> svc.create(3L, "Usluge"));

        assertEquals(502, ex.getStatusCode().value());
        verify(fakture, never()).save(any());
        assertEquals(SettlementStatus.READY_FOR_INVOICE, a.getSettlementStatus());
        assertNull(a.getAgencyInvoice());
    }

    @Test
    void create_praznaStavka_400_iBlokada_422() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> svc.create(3L, "   ")).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> svc.create(3L, "x".repeat(501))).getStatusCode().value());

        when(rezervacije.findCompletedNotInvoiced(3L)).thenReturn(List.of());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> svc.create(3L, "Usluge"));
        assertEquals(422, ex.getStatusCode().value());
        verify(mejl, never()).sendAgencyInvoice(any(), any());
        verify(sekvenca, never()).save(any());
    }

    private AgencyInvoice faktura(AgencyInvoiceStatus status, Booking... bs) {
        AgencyInvoice inv = new AgencyInvoice();
        inv.setId(9L);
        inv.setInvoiceNumber("ESC-AG-2026-0007");
        inv.setAgencyId(3L);
        inv.setAgencyName("Sani Tours");
        inv.setAgencyEmail("sandra@sani.rs");
        inv.setDescription("Usluge");
        inv.setAmount(new BigDecimal("99.50"));
        inv.setBookingCount(bs.length);
        inv.setStatus(status);
        inv.setIssuedAt(LocalDate.of(2026, 9, 16));
        inv.setDueDate(LocalDate.of(2026, 9, 24));
        inv.setPdf("%PDF".getBytes());
        for (Booking b : bs) {
            b.setAgencyInvoice(inv);
            b.setSettlementStatus(status == AgencyInvoiceStatus.PAID ? SettlementStatus.PAID : SettlementStatus.INVOICED);
        }
        when(fakture.findByIdForUpdate(9L)).thenReturn(Optional.of(inv));
        when(fakture.findById(9L)).thenReturn(Optional.of(inv));
        when(rezervacije.findByAgencyInvoiceIdOrderByIdAsc(9L)).thenReturn(List.of(bs));
        return inv;
    }

    @Test
    void placeno_iNazad_prenosiSeNaRezervacije() {
        Booking a = booking(1, "ESC-aaaa1111", LocalDate.of(2026, 9, 5));
        AgencyInvoice inv = faktura(AgencyInvoiceStatus.SENT, a);

        AgencyInvoiceResponse placeno = svc.markPaid(9L);
        assertEquals(AgencyInvoiceStatus.PAID, placeno.status());
        assertNotNull(placeno.paidAt());
        assertEquals(SettlementStatus.PAID, a.getSettlementStatus());
        assertNotNull(a.getAgencyPaidAt());

        assertEquals(409, assertThrows(ResponseStatusException.class, () -> svc.markPaid(9L)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> svc.voidInvoice(9L, "greška")).getStatusCode().value(),
                "plaćena se ne stornira direktno");

        AgencyInvoiceResponse nazad = svc.unmarkPaid(9L);
        assertEquals(AgencyInvoiceStatus.SENT, nazad.status());
        assertNull(nazad.paidAt());
        assertEquals(SettlementStatus.INVOICED, a.getSettlementStatus());
        assertNull(a.getAgencyPaidAt());
        assertEquals(inv, a.getAgencyInvoice(), "rollback uplate ne dira vezu ka fakturi");
    }

    @Test
    void storno_vracaRezervacijeURed_aBrojOstajeUIstoriji() {
        Booking spremna = booking(1, "ESC-aaaa1111", LocalDate.of(2026, 9, 5));
        Booking bezTroskova = booking(2, "ESC-bbbb2222", LocalDate.of(2026, 9, 6));
        faktura(AgencyInvoiceStatus.SENT, spremna, bezTroskova);
        when(kalkulator.calculate(spremna)).thenReturn(spremno("50.00", "0.00"));
        when(kalkulator.calculate(bezTroskova)).thenReturn(nijeSpremno("Nedostaju troskovi"));

        assertEquals(400, assertThrows(ResponseStatusException.class, () -> svc.voidInvoice(9L, " ")).getStatusCode().value());

        AgencyInvoiceResponse r = svc.voidInvoice(9L, "pogrešan iznos");

        assertEquals(AgencyInvoiceStatus.VOIDED, r.status());
        assertEquals("pogrešan iznos", r.voidReason());
        assertEquals("ESC-AG-2026-0007", r.invoiceNumber());
        assertNull(spremna.getAgencyInvoice());
        assertNull(spremna.getAgencyInvoicedAt());
        assertEquals(SettlementStatus.READY_FOR_INVOICE, spremna.getSettlementStatus());
        assertEquals(SettlementStatus.NEEDS_COSTS, bezTroskova.getSettlementStatus());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> svc.voidInvoice(9L, "opet")).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> svc.resend(9L)).getStatusCode().value(),
                "stornirana se ne šalje ponovo");
    }

    @Test
    void ponovo_saljeSacuvaniPdfNaTrenutniMejlAgencije() {
        Booking a = booking(1, "ESC-aaaa1111", LocalDate.of(2026, 9, 5));
        AgencyInvoice inv = faktura(AgencyInvoiceStatus.SENT, a);
        sani.setContactEmail("novi@sani.rs");

        AgencyInvoiceResponse r = svc.resend(9L);

        verify(mejl).sendAgencyInvoice(inv, "%PDF".getBytes());
        assertEquals("novi@sani.rs", r.agencyEmail());
        assertNotNull(r.sentAt());
        verify(pdf, never()).generateAgency(any());

        when(mejl.sendAgencyInvoice(any(), any())).thenReturn(false);
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> svc.resend(9L)).getStatusCode().value());
    }

    @Test
    void pdf_vracaBajtoveIImeFajla() {
        faktura(AgencyInvoiceStatus.SENT);
        AgencyInvoiceService.Pdf p = svc.pdf(9L);
        assertEquals("escapii-faktura-ESC-AG-2026-0007.pdf", p.fileName());
        assertArrayEquals("%PDF".getBytes(), p.bytes());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> svc.pdf(77L)).getStatusCode().value());
    }
}
