package com.escapii.config;

import com.escapii.passport.PassportRetentionService;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.CustomDateInquiryRepository;
import com.escapii.service.AppErrorService;
import com.escapii.service.BookingSchedulingService;
import com.escapii.service.email.DigestEmailService;
import com.escapii.service.impl.ConfirmationDocumentAutoSender;
import com.escapii.service.impl.ExpiredDateCleanup;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pad celog koraka jutarnjeg kruga ide u AppError, a ostali koraci se i dalje izvrše. */
class DailyTaskSchedulerAppErrorTest {

    private static ExpiredDateCleanup cleanupBezIsteklih() {
        ExpiredDateCleanup c = mock(ExpiredDateCleanup.class);
        when(c.obrisiIstekleBezRezervacija(any())).thenReturn(new ExpiredDateCleanup.Rezultat(0, 0, 0));
        return c;
    }

    @Test
    void padKorakaIdeUAppError_ostaliKoraciIduDalje() {
        BookingSchedulingService scheduling = mock(BookingSchedulingService.class);
        AppErrorService greske = mock(AppErrorService.class);
        DailyTaskScheduler scheduler = new DailyTaskScheduler(
                scheduling, mock(BookingRepository.class), mock(DigestEmailService.class),
                mock(AvailableDateRepository.class), mock(CustomDateInquiryRepository.class),
                mock(ConfirmationDocumentAutoSender.class), mock(PassportRetentionService.class), greske,
                cleanupBezIsteklih(), mock(org.springframework.jdbc.core.JdbcTemplate.class));
        IllegalStateException pad = new IllegalStateException("baza nedostupna");
        doThrow(pad).when(scheduling).sendPendingForecasts();

        scheduler.runDailyTasks();

        verify(greske).record("Jutarnji krug: korak prognoze", 0, pad);
        verify(scheduling).sendPendingReveals();
        verify(scheduling).completeFinishedBookings();
    }

    @Test
    void bezPadaNemaGreske() {
        AppErrorService greske = mock(AppErrorService.class);
        DailyTaskScheduler scheduler = new DailyTaskScheduler(
                mock(BookingSchedulingService.class), mock(BookingRepository.class), mock(DigestEmailService.class),
                mock(AvailableDateRepository.class), mock(CustomDateInquiryRepository.class),
                mock(ConfirmationDocumentAutoSender.class), mock(PassportRetentionService.class), greske,
                cleanupBezIsteklih(), mock(org.springframework.jdbc.core.JdbcTemplate.class));

        scheduler.runDailyTasks();

        verify(greske, never()).record(any(), anyInt(), any());
    }

    /**
     * Greška sa produkcije 14.09: brisanje isteklih termina je puklo na FK iz term_destination.
     * Deaktivacija termina sa rezervacijama ne sme da zavisi od toga, pad mora u AppError, a
     * masovni DELETE termina se više ne zove mimo ExpiredDateCleanup.
     */
    @Test
    void padBrisanjaTerminaNePreskaceDeaktivaciju_iIdeUAppError() {
        AppErrorService greske = mock(AppErrorService.class);
        AvailableDateRepository datumi = mock(AvailableDateRepository.class);
        CustomDateInquiryRepository upiti = mock(CustomDateInquiryRepository.class);
        PassportRetentionService pasosi = mock(PassportRetentionService.class);
        ExpiredDateCleanup cleanup = mock(ExpiredDateCleanup.class);
        DataIntegrityViolationException fk = new DataIntegrityViolationException("term_destination FK");
        when(cleanup.obrisiIstekleBezRezervacija(any())).thenThrow(fk);
        DailyTaskScheduler scheduler = new DailyTaskScheduler(
                mock(BookingSchedulingService.class), mock(BookingRepository.class), mock(DigestEmailService.class),
                datumi, upiti, mock(ConfirmationDocumentAutoSender.class), pasosi, greske, cleanup,
                mock(org.springframework.jdbc.core.JdbcTemplate.class));

        scheduler.runDailyTasks();

        InOrder red = inOrder(datumi, cleanup);
        red.verify(datumi).deactivateExpiredWithBookings(any());
        red.verify(cleanup).obrisiIstekleBezRezervacija(any());
        verify(datumi, never()).deleteExpiredWithNoBookings(any());
        verify(greske).record("Jutarnji krug: korak cleanup termina", 0, fk);
        verify(upiti).deleteClosedBefore(any());
        verify(pasosi).purgeExpired(any());
    }
}
