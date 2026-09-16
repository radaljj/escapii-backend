package com.escapii.config;

import com.escapii.dto.AgencySettlementResponse;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.SettlementStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.service.AgencySettlementCalculator;
import com.escapii.service.AppErrorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Ostaci starog toka fakturisanja po rezervaciji (INVOICED/PAID/VOIDED bez zbirne fakture) se
 * pri startu vraćaju u red: stari broj i datumi se brišu, status se izvodi iz kalkulatora.
 */
class LegacySettlementResetTest {

    private final BookingRepository rezervacije = mock(BookingRepository.class);
    private final AgencySettlementCalculator kalkulator = mock(AgencySettlementCalculator.class);
    private final AppErrorService greske = mock(AppErrorService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AppErrorService> provider = mock(ObjectProvider.class);

    private LegacySettlementReset reset() {
        when(provider.getIfAvailable()).thenReturn(greske);
        when(rezervacije.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        // TransactionTemplate nad mock menadzerom: execute() samo pozove callback
        return new LegacySettlementReset(rezervacije, kalkulator, mock(PlatformTransactionManager.class), provider);
    }

    private static Booking stara(long id, String ref, SettlementStatus status, String broj) {
        Booking b = new Booking();
        b.setId(id);
        b.setBookingRef(ref);
        b.setStatus(BookingStatus.COMPLETED);
        b.setSettlementStatus(status);
        b.setAgencyInvoiceNumber(broj);
        b.setAgencyInvoicedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        b.setAgencyPaidAt(status == SettlementStatus.PAID ? LocalDateTime.of(2026, 9, 3, 10, 0) : null);
        b.setAgencyVoidedAt(status == SettlementStatus.VOIDED ? LocalDateTime.of(2026, 9, 4, 10, 0) : null);
        b.setAgencyVoidReason(status == SettlementStatus.VOIDED ? "proba" : null);
        return b;
    }

    private static AgencySettlementResponse spremno(boolean spremno) {
        return AgencySettlementResponse.builder().readyForInvoice(spremno).validationErrors(List.of()).build();
    }

    @Test
    void vracaUred_brisеStareTragove_statusPoKalkulatoru() {
        Booking fakturisana = stara(1, "ESC-aaaa1111", SettlementStatus.INVOICED, "ESC-AG-2026-0001");
        Booking placena     = stara(2, "ESC-bbbb2222", SettlementStatus.PAID,     "ESC-AG-2026-0002");
        Booking stornirana  = stara(3, "ESC-cccc3333", SettlementStatus.VOIDED,   "ESC-AG-2026-0003");
        when(rezervacije.findLegacySettledWithoutInvoice()).thenReturn(List.of(fakturisana, placena, stornirana));
        when(kalkulator.calculate(fakturisana)).thenReturn(spremno(true));
        when(kalkulator.calculate(placena)).thenReturn(spremno(false));
        when(kalkulator.calculate(stornirana)).thenReturn(spremno(true));

        int n = reset().primeni();

        assertEquals(3, n);
        for (Booking b : List.of(fakturisana, placena, stornirana)) {
            assertNull(b.getAgencyInvoiceNumber(), b.getBookingRef() + ": stari broj obrisan");
            assertNull(b.getAgencyInvoicedAt());
            assertNull(b.getAgencyPaidAt());
            assertNull(b.getAgencyVoidedAt());
            assertNull(b.getAgencyVoidReason());
            assertNull(b.getAgencyInvoice());
            verify(rezervacije).save(b);
        }
        assertEquals(SettlementStatus.READY_FOR_INVOICE, fakturisana.getSettlementStatus());
        assertEquals(SettlementStatus.NEEDS_COSTS,       placena.getSettlementStatus());
        assertEquals(SettlementStatus.READY_FOR_INVOICE, stornirana.getSettlementStatus());
        verifyNoInteractions(greske);
    }

    @Test
    void bezZaostalih_nistaNeDira() {
        when(rezervacije.findLegacySettledWithoutInvoice()).thenReturn(List.of());

        LegacySettlementReset r = reset();
        r.naStartu();

        verify(rezervacije, never()).save(any());
        verifyNoInteractions(kalkulator, greske);
    }

    @Test
    void kalkulatorPukne_statusNeedsCosts_aOstaloSeIpakResetuje() {
        Booking b = stara(5, "ESC-eeee5555", SettlementStatus.INVOICED, "ESC-AG-2026-0005");
        when(rezervacije.findLegacySettledWithoutInvoice()).thenReturn(List.of(b));
        when(kalkulator.calculate(b)).thenThrow(new IllegalStateException("nema stavki"));

        assertEquals(1, reset().primeni());

        assertEquals(SettlementStatus.NEEDS_COSTS, b.getSettlementStatus());
        assertNull(b.getAgencyInvoiceNumber());
    }

    @Test
    void greskaPriStartu_ideUAppError_neObaraStart() {
        when(rezervacije.findLegacySettledWithoutInvoice()).thenThrow(new RuntimeException("kolona ne postoji"));

        LegacySettlementReset r = reset();
        assertDoesNotThrow(r::naStartu);

        verify(greske).record(eq("legacy-settlement-reset"), anyInt(), any(RuntimeException.class));
    }
}
