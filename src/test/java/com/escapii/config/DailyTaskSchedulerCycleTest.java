package com.escapii.config;

import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.passport.PassportRetentionService;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.CustomDateInquiryRepository;
import com.escapii.service.AppErrorService;
import com.escapii.service.BookingSchedulingService;
import com.escapii.service.email.DigestEmailService;
import com.escapii.service.impl.ConfirmationDocumentAutoSender;
import com.escapii.service.impl.ExpiredDateCleanup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Krug na svakih 30 minuta: slanja idu svaki put (idempotentna su), dnevni koraci samo kad
 * prijava u bazi prođe - tačno jednom dnevno, i posle restarta. Bez baze: dnevni koraci čekaju
 * sledeći krug (nema rezerve po vremenu - pustila bi ih dvaput).
 */
class DailyTaskSchedulerCycleTest {

    private final BookingSchedulingService scheduling = mock(BookingSchedulingService.class);
    private final BookingRepository rezervacije = mock(BookingRepository.class);
    private final DigestEmailService digest = mock(DigestEmailService.class);
    private final AppErrorService greske = mock(AppErrorService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private DailyTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        ExpiredDateCleanup cleanup = mock(ExpiredDateCleanup.class);
        when(cleanup.obrisiIstekleBezRezervacija(any())).thenReturn(new ExpiredDateCleanup.Rezultat(0, 0, 0));
        scheduler = new DailyTaskScheduler(scheduling, rezervacije, digest,
                mock(AvailableDateRepository.class), mock(CustomDateInquiryRepository.class),
                mock(ConfirmationDocumentAutoSender.class), mock(PassportRetentionService.class), greske,
                cleanup, jdbc);
    }

    private static Booking rez(String ref, LocalDate polazak) {
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(polazak);
        Booking b = new Booking();
        b.setBookingRef(ref);
        b.setFirstName("Ana");
        b.setLastName("Anić");
        b.setDepartureAirport("BEG");
        b.setSelectedDate(d);
        return b;
    }

    @Test
    void slanjaIduSvakiKrug_dnevniKoraciSamoKadPrijavaProdje() {
        // prvi krug dana: UPDATE pogodi red (1); drugi krug istog dana: nista (0)
        when(jdbc.update(startsWith("UPDATE scheduler_runs"), any(), any(), any(), any())).thenReturn(1, 0);

        scheduler.runCycle();
        scheduler.runCycle();

        verify(scheduling, times(2)).sendPendingForecasts();
        verify(scheduling, times(2)).sendPendingReveals();
        verify(scheduling, times(1)).completeFinishedBookings();
        verify(jdbc, times(2)).update(startsWith("INSERT INTO scheduler_runs"), any(Object[].class));
    }

    @Test
    void prviRedIkad_posle1030_seSejeDanasnjimDatumom_daDeployNePonoviDnevniKrug() {
        LocalDate danas = LocalDate.of(2026, 9, 17);

        scheduler.preuzmiDnevniKrug(danas, LocalTime.of(10, 0));
        verify(jdbc).update(startsWith("INSERT INTO scheduler_runs"), eq("daily"));

        scheduler.preuzmiDnevniKrug(danas, LocalTime.of(14, 0));
        verify(jdbc).update(startsWith("INSERT INTO scheduler_runs"), eq("daily"), eq(java.sql.Date.valueOf(danas)));
    }

    @Test
    void redPostojiOdStarta_aPadUpisaNeObaraStart() {
        // health gleda red u scheduler_runs, pa red mora postojati i pre prvog kruga (do 30 min posle deploya)
        scheduler.osigurajRedPriStartu();
        verify(jdbc).update(startsWith("INSERT INTO scheduler_runs"), any(Object[].class));
        verify(jdbc, never()).update(startsWith("UPDATE scheduler_runs"), any(Object[].class));

        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("nema tabele"));
        assertDoesNotThrow(scheduler::osigurajRedPriStartu);
    }

    @Test
    void prognozaPaReveal_uSvakomKrugu() {
        when(jdbc.update(startsWith("UPDATE scheduler_runs"), any(), any(), any(), any())).thenReturn(0);

        scheduler.runCycle();

        var redom = inOrder(scheduling);
        redom.verify(scheduling).sendPendingForecasts();
        redom.verify(scheduling).sendPendingReveals();
        verify(scheduling, never()).completeFinishedBookings();
        verifyNoInteractions(digest);
    }

    @Test
    void padPrijave_dnevniKoraciSePreskacu_iAppError() {
        // Bez baze ni dnevni koraci ne bi radili; rezerva "po vremenu" bi ih mogla pustiti dvaput
        // (pad u 10:00 pa uspesna prijava u 10:30) - zato se preskacu do sledeceg kruga.
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("baza nedostupna"));
        LocalDate danas = LocalDate.of(2026, 9, 17);

        assertFalse(scheduler.preuzmiDnevniKrug(danas, LocalTime.of(10, 0)));
        assertFalse(scheduler.preuzmiDnevniKrug(danas, LocalTime.of(10, 30)));
        verify(greske, atLeastOnce()).record(eq("Jutarnji krug: prijava dnevnog kruga"), anyInt(), any(RuntimeException.class));

        scheduler.runCycle();
        verify(scheduling).sendPendingForecasts();
        verify(scheduling, never()).completeFinishedBookings();
    }

    @Test
    void bezDestinacije_upozorenjeUGreskeIDigest_hitnoPosebno() {
        LocalDate danas = LocalDate.now();
        Booking uskoro = rez("ESC-aaaa1111", danas.plusDays(2));   // hitno (<= 3 dana)
        Booking kasnije = rez("ESC-bbbb2222", danas.plusDays(6));  // samo upozorenje
        when(rezervacije.findConfirmedWithoutDestination(danas, danas.plusDays(7))).thenReturn(List.of(uskoro, kasnije));
        when(rezervacije.findConfirmedDepartingBetween(any(), any())).thenReturn(List.of());

        scheduler.runDailySteps();

        ArgumentCaptor<Exception> greska = ArgumentCaptor.forClass(Exception.class);
        verify(greske, times(2)).record(eq("Jutarnji krug: nema destinacije"), eq(0), greska.capture());
        List<Exception> zabelezene = greska.getAllValues();
        assertTrue(zabelezene.get(0) instanceof DailyTaskScheduler.NemaDestinacije);
        assertTrue(zabelezene.get(0).getMessage().contains("ESC-aaaa1111") && zabelezene.get(0).getMessage().contains("ESC-bbbb2222"));
        assertTrue(zabelezene.get(1) instanceof DailyTaskScheduler.NemaDestinacijeHitno);
        assertTrue(zabelezene.get(1).getMessage().contains("ESC-aaaa1111"), "hitno pominje samo skoru");
        assertFalse(zabelezene.get(1).getMessage().contains("ESC-bbbb2222"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Booking>> bez = ArgumentCaptor.forClass(List.class);
        verify(digest).sendDailyDigest(eq(danas), any(), any(), any(), any(), any(), any(), bez.capture());
        assertEquals(List.of(uskoro, kasnije), bez.getValue());
    }

    @Test
    void bezRezervacija_nemaUpozorenjaNiDigesta_aOtvorenaUpozorenjaSeZatvaraju() {
        when(rezervacije.findConfirmedWithoutDestination(any(), any())).thenReturn(List.of());
        com.escapii.model.AppError otvoreno = new com.escapii.model.AppError();
        otvoreno.setId(5L); otvoreno.setEndpoint("Jutarnji krug: nema destinacije"); otvoreno.setResolved(false);
        com.escapii.model.AppError drugo = new com.escapii.model.AppError();
        drugo.setId(6L); drugo.setEndpoint("Jutarnji krug: prognoza"); drugo.setResolved(false);
        when(greske.getAll()).thenReturn(List.of(otvoreno, drugo));

        scheduler.runDailySteps();

        verify(greske, never()).record(any(), anyInt(), any());
        verify(greske).resolve(5L);
        verify(greske, never()).resolve(6L);
        verifyNoInteractions(digest);
    }
}
