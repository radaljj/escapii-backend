package com.escapii.service.flow;

import com.escapii.dto.BookingRequest;
import com.escapii.dto.PricePreviewResponse;
import com.escapii.mapper.BookingMapper;
import com.escapii.model.AccommodationType;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.promo.ExclusionPromo;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.DestinationRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.service.PriceCalculator;
import com.escapii.service.impl.BookingServiceImpl;
import com.escapii.service.impl.FinancialItemSnapshotService;
import com.escapii.service.impl.VoucherLedger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Promo kod uz rezervaciju: važenje proverava backend (sajtu se ne veruje), kod koji više ne
 * važi ODBIJA rezervaciju - kupac je pristao na cenu sa pogodnošću, pa mora da vidi novu pre nego
 * što se obaveže - a iskorišćen promo ostaje zapisan na rezervaciji (panel, mejl, statistika).
 */
class BookingPromoFlowTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final AvailableDateRepository availableDateRepository = mock(AvailableDateRepository.class);
    private final PriceCalculator priceCalculator = mock(PriceCalculator.class);
    private final ExclusionPromo promo = mock(ExclusionPromo.class);
    private BookingServiceImpl svc;

    @BeforeEach
    void setUp() {
        svc = new BookingServiceImpl(bookingRepository, availableDateRepository, mock(DestinationRepository.class),
                mock(GiftVoucherRepository.class), priceCalculator, mock(ApplicationEventPublisher.class),
                mock(BookingMapper.class), new FinancialItemSnapshotService(), new VoucherLedger(), promo);

        AvailableDate d = new AvailableDate();
        d.setId(10L); d.setActive(true); d.setDepartureAirport("BEG");
        d.setDepartureDate(LocalDate.now().plusDays(30)); d.setReturnDate(LocalDate.now().plusDays(33));
        d.setNumberOfNights(3); d.setAvailableSlots(5); d.setBasePrice(500);
        when(availableDateRepository.findById(10L)).thenReturn(Optional.of(d));
        when(bookingRepository.existsPendingDuplicate(anyString(), anyLong())).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) b.setId(1L);
            return b;
        });
    }

    private static BookingRequest zahtev(String promoCode) {
        BookingRequest r = new BookingRequest();
        r.setDepartureAirport("BEG"); r.setNumberOfTravelers(2); r.setSelectedDateId(10L);
        r.setAccommodationType(AccommodationType.STANDARD); r.setCabinSuitcaseCount(0);
        r.setPassengers(List.of(
                new BookingRequest.PassengerInfo("Ana Anić", "F", LocalDate.of(1992, 5, 5), null, true, "Srbija", "AA1234567"),
                new BookingRequest.PassengerInfo("Marko Marković", "M", LocalDate.of(1990, 1, 1), null, true, "Srbija", "BB1234567")));
        r.setFirstName("Ana"); r.setLastName("Anić"); r.setEmail("ana@example.com"); r.setPhone("+381601234567");
        r.setAcceptedTerms(true); r.setAcceptedPrivacy(true); r.setAcceptedGdpr(true);
        r.setConsentVersion("2026-08-24"); r.setConsentLang("sr"); r.setFormDuration(20);
        r.setPromoCode(promoCode);
        return r;
    }

    private static PricePreviewResponse cena(boolean promo) {
        return PricePreviewResponse.builder()
                .basePricePerPerson(500).accommodationExtraPerPerson(0).breakfastPerPerson(0).seatsTogether(0)
                .insurancePerPerson(0).eurPerPerson(500).soloSurcharge(0).cabinSuitcaseCount(0).cabinSuitcaseTotal(0)
                .revealBoxTotal(0).exclusionCount(0).numberOfTravelers(2).numberOfNights(3)
                .exclusionCostFlat(promo ? 0 : 60).totalEurAll(promo ? 1000 : 1060)
                .exclusionPromoApplied(promo).exclusionPromoSavedEur(promo ? 60 : 0)
                .build();
    }

    private void kalkulatorVraca(boolean promo) {
        when(priceCalculator.calculate(any(), anyInt(), any(), anyInt(), anyInt(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyString(), eq(promo))).thenReturn(cena(promo));
    }

    private Booking sacuvana() {
        ArgumentCaptor<Booking> cap = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository, atLeastOnce()).save(cap.capture());
        return cap.getValue();
    }

    @Test
    void vazeciKod_iskljucivanjaBesplatna_iPromoZapisanNaRezervaciji() {
        when(promo.vazi("SKIP3")).thenReturn(true);
        kalkulatorVraca(true);

        svc.createBooking(zahtev(" skip3 "));   // sajt šalje kako je kupac ukucao

        Booking b = sacuvana();
        assertEquals("SKIP3", b.getPromoCode());
        assertEquals(60, b.getPromoSavedEur());
        assertEquals(0, b.getExclusionCostEur());
        assertEquals(1000, b.getTotalPriceAll());
    }

    @Test
    void bezKoda_punaCena_iNistaOPromoNaRezervaciji() {
        kalkulatorVraca(false);

        svc.createBooking(zahtev(null));

        Booking b = sacuvana();
        assertNull(b.getPromoCode());
        assertNull(b.getPromoSavedEur());
        assertEquals(1060, b.getTotalPriceAll());
        verify(promo, never()).vazi(any());
    }

    @Test
    void prazanKod_jeIstoStoIBezKoda() {
        kalkulatorVraca(false);
        svc.createBooking(zahtev("   "));
        assertNull(sacuvana().getPromoCode());
    }

    @Test
    void kodKojiViseNeVazi_odbijaRezervaciju_daKupacVidiNovuCenu() {
        when(promo.vazi("SKIP3")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> svc.createBooking(zahtev("SKIP3")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(ExclusionPromo.PORUKA_NE_VAZI, ex.getReason());
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(priceCalculator);
    }

    @Test
    void pregledCene_nevazeciKodSeSamoIgnorise_aSajtSaznaDaLiPromoTraje() {
        when(promo.vazi("STARI")).thenReturn(false);
        when(promo.aktivan()).thenReturn(true);
        kalkulatorVraca(false);

        PricePreviewResponse p = svc.previewPrice(10L, 2, AccommodationType.STANDARD, 4, 0, "STARI", false, false, false);

        assertFalse(p.getExclusionPromoApplied());
        assertEquals(60, p.getExclusionCostFlat());
        assertTrue(p.getExclusionPromoActive(), "korak sa isključivanjem tada podseća na kod");
    }

    @Test
    void pregledCene_saVazecimKodom() {
        when(promo.vazi("SKIP3")).thenReturn(true);
        when(promo.aktivan()).thenReturn(true);
        kalkulatorVraca(true);

        PricePreviewResponse p = svc.previewPrice(10L, 2, AccommodationType.STANDARD, 4, 0, "SKIP3", false, false, false);

        assertTrue(p.getExclusionPromoApplied());
        assertEquals(0, p.getExclusionCostFlat());
        assertEquals(60, p.getExclusionPromoSavedEur());
    }
}
