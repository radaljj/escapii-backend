package com.escapii.service.flow;

import com.escapii.model.Booking;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.service.email.ForecastEmailService;
import com.escapii.service.email.RevealEmailService;
import com.escapii.service.impl.BookingSchedulingServiceImpl;
import com.escapii.service.impl.ConfirmationDocumentAutoSender;
import com.escapii.service.weather.DailyForecast;
import com.escapii.service.weather.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Ručni admin resend (dugme u panelu, van scheduler-a) mora pratiti isto pravilo
 * kao automatski scheduler i InvoiceServiceImpl: mejl prvo, "poslato" flag tek na
 * uspeh. Ranije se flag upisivao PRE slanja - radilo je samo zato što je metoda
 * @Transactional pa bi rollback pokupio i taj upis, ali je neuspeh izlazio kao
 * generički 500 (spamuje AppError alert) umesto jasne "pokušaj ponovo" poruke.
 */
@ExtendWith(MockitoExtension.class)
class ManualResendSentFlagTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private GiftVoucherRepository giftVoucherRepository;
    @Mock private RevealEmailService revealEmailService;
    @Mock private ForecastEmailService forecastEmailService;
    @Mock private WeatherService weatherService;
    @Mock private ConfirmationDocumentAutoSender confirmationDocumentAutoSender;

    private BookingSchedulingServiceImpl svc;

    @BeforeEach
    void setUp() {
        svc = new BookingSchedulingServiceImpl(
                bookingRepository, giftVoucherRepository, revealEmailService, forecastEmailService,
                weatherService, confirmationDocumentAutoSender, new com.escapii.service.impl.VoucherLedger(),
                org.mockito.Mockito.mock(com.escapii.service.AppErrorService.class),
                org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
    }

    private Booking booking() {
        return booking(LocalDate.now().plusDays(3));
    }

    private Booking booking(LocalDate polazak) {
        Booking b = new Booking();
        b.setId(42L);
        b.setBookingRef("ESC-test0042");
        b.setAssignedDestination("Barselona");
        com.escapii.model.AvailableDate termin = new com.escapii.model.AvailableDate();
        termin.setDepartureDate(polazak);
        termin.setReturnDate(polazak.plusDays(3));
        b.setSelectedDate(termin);
        return b;
    }

    /** Ono što servis stvarno vraća: 16 dana, danas + 15. */
    private static List<DailyForecast> sesnaestDana() {
        java.util.List<DailyForecast> f = new java.util.ArrayList<>();
        for (int i = 0; i < 16; i++) f.add(new DailyForecast(LocalDate.now().plusDays(i), 0, 25, 15, 0.0));
        return f;
    }

    /**
     * Ručno „Pošalji prognozu" 22 dana pre polaska: prognoza ne doseže dan polaska, pa bi mejl
     * imao samo „Trenutno vreme" bez dana puta, a upisano „poslato" bi ugasilo automatsku na T-7.
     * Zato 409 sa objašnjenjem, bez slanja i bez upisa.
     */
    @Test
    void rucnaPrognozaPreDometaSeOdbijaBezSlanjaIUpisa() {
        Booking b = booking(LocalDate.now().plusDays(22));
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));
        when(weatherService.getForecast(anyString())).thenReturn(Optional.of(sesnaestDana()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.sendForecastForBooking(42L));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("16 dana"), ex.getReason());
        assertTrue(ex.getReason().contains("za 22 dana"), ex.getReason());
        assertNull(b.getForecastSentAt(), "prognoza van dometa ne sme biti evidentirana kao poslata");
        verify(forecastEmailService, never()).sendForecastEmail(any(), any());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    /** Polazak za 15 dana je poslednji dan koji servis pokriva - ručno slanje prolazi. */
    @Test
    void rucnaPrognozaNaIviciDometaProlazi() {
        Booking b = booking(LocalDate.now().plusDays(15));
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));
        when(weatherService.getForecast(anyString())).thenReturn(Optional.of(sesnaestDana()));

        svc.sendForecastForBooking(42L);

        assertNotNull(b.getForecastSentAt());
        verify(forecastEmailService).sendForecastEmail(eq(b), any());
    }

    /**
     * Automatski krug: servis vrati kraći niz koji ne doseže polazak (zakazao). Ne šalje se
     * krnji mejl i ne upisuje se „poslato" - sutra se pokušava ponovo.
     */
    @Test
    void automatskaPrognozaBezDanaPolaskaSePreskaceBezUpisa() {
        Booking b = booking(LocalDate.now().plusDays(7));
        when(bookingRepository.findReadyForForecast(any(), any())).thenReturn(List.of(b));
        when(weatherService.getForecast(anyString())).thenReturn(Optional.of(
                List.of(new DailyForecast(LocalDate.now(), 0, 25, 15, 0.0),
                        new DailyForecast(LocalDate.now().plusDays(1), 0, 25, 15, 0.0))));

        svc.sendPendingForecasts();

        assertNull(b.getForecastSentAt());
        verify(forecastEmailService, never()).sendForecastEmail(any(), any());
        verify(bookingRepository, never()).markForecastSent(anyLong(), any());
    }

    @Test
    void neuspesanRucniRevealVracaBadGatewayINeUpisujeFlag() {
        Booking b = booking();
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));
        doThrow(new RuntimeException("[Reveal] Email slanje nije uspelo"))
                .when(revealEmailService).sendRevealEmail(eq(b), any());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.sendRevealForBooking(42L, null));

        assertEquals(502, ex.getStatusCode().value());
        assertNull(b.getRevealSentAt(), "flag ne sme biti upisan kad slanje padne");
        verify(bookingRepository, never()).save(argThat(saved -> saved.getRevealSentAt() != null));
    }

    @Test
    void uspesanRucniRevealUpisujeFlagTekPoslePotvrdjenogSlanja() {
        Booking b = booking();
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));
        // sendRevealEmail je void - default mock ponašanje (ne baca) simulira uspeh

        svc.sendRevealForBooking(42L, null);

        assertNotNull(b.getRevealSentAt());
        verify(revealEmailService).sendRevealEmail(eq(b), any());
        verify(bookingRepository).save(b);
    }

    @Test
    void neuspesnaRucnaPrognozaVracaBadGatewayINeUpisujeFlag() {
        Booking b = booking();
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));
        when(weatherService.getForecast(anyString())).thenReturn(Optional.of(
                List.of(new DailyForecast(LocalDate.now().plusDays(3), 0, 25, 15, 0.0))));
        doThrow(new RuntimeException("[Forecast] Email slanje nije uspelo"))
                .when(forecastEmailService).sendForecastEmail(eq(b), any());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.sendForecastForBooking(42L));

        assertEquals(502, ex.getStatusCode().value());
        assertNull(b.getForecastSentAt(), "flag ne sme biti upisan kad slanje padne");
        verify(bookingRepository, never()).save(argThat(saved -> saved.getForecastSentAt() != null));
    }

    @Test
    void uspesnaRucnaPrognozaUpisujeFlagTekPoslePotvrdjenogSlanja() {
        Booking b = booking();
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));
        when(weatherService.getForecast(anyString())).thenReturn(Optional.of(
                List.of(new DailyForecast(LocalDate.now().plusDays(3), 0, 25, 15, 0.0))));

        svc.sendForecastForBooking(42L);

        assertNotNull(b.getForecastSentAt());
        verify(forecastEmailService).sendForecastEmail(eq(b), any());
        verify(bookingRepository).save(b);
    }
}
