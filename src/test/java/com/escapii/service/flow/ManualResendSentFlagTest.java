package com.escapii.service.flow;

import com.escapii.model.Booking;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.service.email.ForecastEmailService;
import com.escapii.service.email.RevealEmailService;
import com.escapii.service.impl.BookingSchedulingServiceImpl;
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

    private BookingSchedulingServiceImpl svc;

    @BeforeEach
    void setUp() {
        svc = new BookingSchedulingServiceImpl(
                bookingRepository, giftVoucherRepository, revealEmailService, forecastEmailService, weatherService);
    }

    private Booking booking() {
        Booking b = new Booking();
        b.setId(42L);
        b.setBookingRef("ESC-test0042");
        b.setAssignedDestination("Barselona");
        return b;
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
