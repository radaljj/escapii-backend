package com.escapii.config;

import com.escapii.dto.AgencySettlementResponse;
import com.escapii.model.AllocationType;
import com.escapii.model.Booking;
import com.escapii.model.BookingFinancialItem;
import com.escapii.model.ItemType;
import com.escapii.model.SettlementStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.service.AgencySettlementCalculator;
import com.escapii.service.AppErrorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Stare rezervacije koje solo doplatu nose kao zasebnu stavku se pri startu prepakuju: 60 €
 * ulazi u osnovni paket (gde se deli 50/50), zasebna stavka nestaje, bruto vrednost ostaje ista.
 */
class SoloSurchargeMergeTest {

    private final BookingRepository rezervacije = mock(BookingRepository.class);
    private final AgencySettlementCalculator kalkulator = mock(AgencySettlementCalculator.class);
    private final AppErrorService greske = mock(AppErrorService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AppErrorService> provider = mock(ObjectProvider.class);

    private SoloSurchargeMerge merge() {
        when(provider.getIfAvailable()).thenReturn(greske);
        when(rezervacije.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        // TransactionTemplate nad mock menadzerom: execute() samo pozove callback
        return new SoloSurchargeMerge(rezervacije, kalkulator, mock(PlatformTransactionManager.class), provider);
    }

    private static Booking solo(String ref, int paket, int doplata) {
        Booking b = new Booking();
        b.setId(1L);
        b.setBookingRef(ref);
        b.setSettlementStatus(SettlementStatus.NEEDS_COSTS);
        b.setFinancialItems(new ArrayList<>(List.of(
                stavka(b, ItemType.BASE_PACKAGE, AllocationType.MARGIN_50_50, "Osnovni paket (let + hotel)", 1, paket),
                stavka(b, ItemType.SOLO_SURCHARGE, AllocationType.ESCAPII_100, "Doplata za solo putnika", 1, doplata))));
        return b;
    }

    private static BookingFinancialItem stavka(Booking b, ItemType tip, AllocationType alokacija,
                                               String opis, int kolicina, int iznos) {
        BookingFinancialItem i = new BookingFinancialItem();
        i.setBooking(b);
        i.setItemType(tip);
        i.setAllocationType(alokacija);
        i.setDescription(opis);
        i.setQuantity(kolicina);
        i.setUnitCustomerPrice(new BigDecimal(iznos).setScale(2));
        i.setCustomerTotal(new BigDecimal(iznos).setScale(2));
        return i;
    }

    private static AgencySettlementResponse spremno(boolean spremno) {
        return AgencySettlementResponse.builder().readyForInvoice(spremno).validationErrors(List.of()).build();
    }

    @Test
    void doplataUlaziUPaket_zasebnaStavkaNestaje_ukupnoIsto() {
        Booking b = solo("ESC-aaaa1111", 300, 60);
        when(rezervacije.findWithSoloSurchargeItem()).thenReturn(List.of(b));
        when(kalkulator.calculate(b)).thenReturn(spremno(false));

        assertEquals(1, merge().primeni());

        assertEquals(1, b.getFinancialItems().size(), "ostaje samo osnovni paket");
        BookingFinancialItem paket = b.getFinancialItems().get(0);
        assertEquals(ItemType.BASE_PACKAGE, paket.getItemType());
        assertEquals(new BigDecimal("360.00"), paket.getCustomerTotal(), "300 + 60 - novac se ne gubi");
        assertEquals(new BigDecimal("360.00"), paket.getUnitCustomerPrice());
        assertTrue(paket.getDescription().contains("solo"), paket.getDescription());
        verify(rezervacije).save(b);
    }

    @Test
    void drugiPutNemaPosla() {
        when(rezervacije.findWithSoloSurchargeItem()).thenReturn(List.of());

        SoloSurchargeMerge m = merge();
        m.naStartu();

        verify(rezervacije, never()).save(any());
        verifyNoInteractions(kalkulator, greske);
    }

    @Test
    void blokiranaNegativnomMarzom_postajeSpremnaZaFakturu() {
        Booking b = solo("ESC-bbbb2222", 300, 60);
        when(rezervacije.findWithSoloSurchargeItem()).thenReturn(List.of(b));
        // posle prepakivanja marza paketa raste (kupac 360 umesto 300), pa kalkulator kaze "spremno"
        when(kalkulator.calculate(b)).thenReturn(spremno(true));

        merge().primeni();

        assertEquals(SettlementStatus.READY_FOR_INVOICE, b.getSettlementStatus());
    }

    @Test
    void fakturisanStatusSeNeDira() {
        Booking b = solo("ESC-cccc3333", 300, 60);
        b.setSettlementStatus(SettlementStatus.INVOICED);
        when(rezervacije.findWithSoloSurchargeItem()).thenReturn(List.of(b));

        merge().primeni();

        assertEquals(SettlementStatus.INVOICED, b.getSettlementStatus(), "status se ne prepravlja");
        verifyNoInteractions(kalkulator);
    }

    @Test
    void bezOsnovnogPaketa_stavkaOstajeNetaknuta() {
        Booking b = new Booking();
        b.setBookingRef("ESC-dddd4444");
        b.setSettlementStatus(SettlementStatus.NEEDS_COSTS);
        b.setFinancialItems(new ArrayList<>(List.of(
                stavka(b, ItemType.SOLO_SURCHARGE, AllocationType.ESCAPII_100, "Doplata za solo putnika", 1, 60))));
        when(rezervacije.findWithSoloSurchargeItem()).thenReturn(List.of(b));

        assertEquals(0, merge().primeni(), "nema gde da ode - radije ostavi kako jeste");

        assertEquals(1, b.getFinancialItems().size());
        verify(rezervacije, never()).save(any());
    }

    @Test
    void greskaPriStartu_ideUAppError_neObaraStart() {
        when(rezervacije.findWithSoloSurchargeItem()).thenThrow(new RuntimeException("kolona ne postoji"));

        SoloSurchargeMerge m = merge();
        assertDoesNotThrow(m::naStartu);

        verify(greske).record(eq("solo-surcharge-merge"), anyInt(), any(RuntimeException.class));
    }
}
