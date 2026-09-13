package com.escapii.service.impl;

import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.service.AppErrorService;
import com.escapii.service.email.ForecastEmailService;
import com.escapii.service.email.RevealEmailService;
import com.escapii.service.weather.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pad u jutarnjem krugu mora da stigne u AppError (mejl + tab Greške), a ne samo u log:
 * server radi, health je zelen, i bez ovoga niko ne primeti da kupac nije dobio reveal.
 */
class JutarnjiKrugAppErrorTest {

    private BookingRepository repo;
    private RevealEmailService reveal;
    private ForecastEmailService prognoza;
    private WeatherService vreme;
    private AppErrorService greske;
    private BookingSchedulingServiceImpl svc;

    @BeforeEach
    void setUp() {
        repo = mock(BookingRepository.class);
        reveal = mock(RevealEmailService.class);
        prognoza = mock(ForecastEmailService.class);
        vreme = mock(WeatherService.class);
        greske = mock(AppErrorService.class);
        svc = new BookingSchedulingServiceImpl(repo, mock(GiftVoucherRepository.class), reveal, prognoza,
                vreme, mock(ConfirmationDocumentAutoSender.class), new VoucherLedger(), greske);
    }

    private static Booking booking(int danaDoPolaska) {
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(LocalDate.now().plusDays(danaDoPolaska));
        d.setReturnDate(LocalDate.now().plusDays(danaDoPolaska + 3));
        Booking b = new Booking();
        b.setId(7L);
        b.setBookingRef("ESC-test0007");
        b.setStatus(BookingStatus.CONFIRMED);
        b.setAssignedDestination("Prag");
        b.setSelectedDate(d);
        return b;
    }

    @Test
    void padSlanjaRevealaIdeUAppError_flagSeNeUpisuje() {
        Booking b = booking(2);
        b.setForecastSentAt(LocalDateTime.now().minusDays(5));
        b.setRevealToken("tok");
        when(repo.findReadyForReveal(any())).thenReturn(List.of(b));
        when(repo.jeLiJosZaReveal(7L)).thenReturn(1L);
        doThrow(new RuntimeException("[Reveal] Email slanje nije uspelo za ESC-test0007"))
                .when(reveal).sendRevealEmail(b);

        svc.sendPendingReveals();

        ArgumentCaptor<Exception> cap = ArgumentCaptor.forClass(Exception.class);
        verify(greske).record(eq(BookingSchedulingServiceImpl.GRESKA_REVEAL), eq(0), cap.capture());
        assertInstanceOf(BookingSchedulingServiceImpl.JutarnjiKrugGreska.class, cap.getValue());
        assertTrue(cap.getValue().getMessage().contains("ESC-test0007"), cap.getValue().getMessage());
        assertTrue(cap.getValue().getMessage().contains("polazak"), cap.getValue().getMessage());
        assertTrue(cap.getValue().getMessage().contains("Email slanje nije uspelo"), cap.getValue().getMessage());
        verify(repo, never()).markRevealSent(any(), any());
    }

    @Test
    void padSlanjaPrognozeIdeUAppError() {
        Booking b = booking(6);
        when(repo.findReadyForForecast(any(), any())).thenReturn(List.of(b));
        when(vreme.getForecast("Prag")).thenReturn(Optional.of(List.of()));
        when(repo.jeLiJosZaPrognozu(7L)).thenReturn(1L);
        doThrow(new RuntimeException("SMTP pao")).when(prognoza).sendForecastEmail(eq(b), any());

        svc.sendPendingForecasts();

        verify(greske).record(eq(BookingSchedulingServiceImpl.GRESKA_PROGNOZA), eq(0),
                any(BookingSchedulingServiceImpl.JutarnjiKrugGreska.class));
        verify(repo, never()).markForecastSent(any(), any());
    }

    @Test
    void nedostupnaPrognozaBlizuPolaskaJeGreska() {
        Booking b = booking(2);
        when(repo.findReadyForForecast(any(), any())).thenReturn(List.of(b));
        when(vreme.getForecast("Prag")).thenReturn(Optional.empty());

        svc.sendPendingForecasts();

        ArgumentCaptor<Exception> cap = ArgumentCaptor.forClass(Exception.class);
        verify(greske).record(eq(BookingSchedulingServiceImpl.GRESKA_PROGNOZA_NEDOSTUPNA), eq(0), cap.capture());
        assertTrue(cap.getValue().getMessage().contains("ESC-test0007"));
        assertTrue(cap.getValue().getMessage().contains("Prag"));
    }

    @Test
    void nedostupnaPrognozaDalekoOdPolaskaNijeGreska_sutraSePokusavaOpet() {
        Booking b = booking(PROGNOZA_DALEKO);
        when(repo.findReadyForForecast(any(), any())).thenReturn(List.of(b));
        when(vreme.getForecast("Prag")).thenReturn(Optional.empty());

        svc.sendPendingForecasts();

        verify(greske, never()).record(any(), anyInt(), any());
    }

    private static final int PROGNOZA_DALEKO = BookingSchedulingServiceImpl.PROGNOZA_HITNO_DANA + 1;

    @Test
    void uspesanRevealNePraviGresku() {
        Booking b = booking(2);
        b.setForecastSentAt(LocalDateTime.now().minusDays(5));
        b.setRevealToken("tok");
        when(repo.findReadyForReveal(any())).thenReturn(List.of(b));
        when(repo.jeLiJosZaReveal(7L)).thenReturn(1L);

        svc.sendPendingReveals();

        verify(repo).markRevealSent(eq(7L), any());
        verify(greske, never()).record(any(), anyInt(), any());
    }

    @Test
    void kvarAppErrorServisaNeRusiPetlju() {
        Booking prva = booking(2);
        prva.setForecastSentAt(LocalDateTime.now().minusDays(5));
        prva.setRevealToken("tok");
        Booking druga = booking(2);
        druga.setId(8L);
        druga.setBookingRef("ESC-test0008");
        druga.setForecastSentAt(LocalDateTime.now().minusDays(5));
        druga.setRevealToken("tok2");
        when(repo.findReadyForReveal(any())).thenReturn(List.of(prva, druga));
        when(repo.jeLiJosZaReveal(anyLong())).thenReturn(1L);
        doThrow(new RuntimeException("SMTP")).when(reveal).sendRevealEmail(prva);
        doThrow(new IllegalStateException("baza")).when(greske).record(any(), anyInt(), any());

        assertDoesNotThrow(svc::sendPendingReveals);

        verify(repo).markRevealSent(eq(8L), any());
    }
}
