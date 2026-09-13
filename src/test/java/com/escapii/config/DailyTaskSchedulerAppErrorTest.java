package com.escapii.config;

import com.escapii.passport.PassportRetentionService;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.CustomDateInquiryRepository;
import com.escapii.service.AppErrorService;
import com.escapii.service.BookingSchedulingService;
import com.escapii.service.email.DigestEmailService;
import com.escapii.service.impl.ConfirmationDocumentAutoSender;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pad celog koraka jutarnjeg kruga ide u AppError, a ostali koraci se i dalje izvrše. */
class DailyTaskSchedulerAppErrorTest {

    @Test
    void padKorakaIdeUAppError_ostaliKoraciIduDalje() {
        BookingSchedulingService scheduling = mock(BookingSchedulingService.class);
        AppErrorService greske = mock(AppErrorService.class);
        DailyTaskScheduler scheduler = new DailyTaskScheduler(
                scheduling, mock(BookingRepository.class), mock(DigestEmailService.class),
                mock(AvailableDateRepository.class), mock(CustomDateInquiryRepository.class),
                mock(ConfirmationDocumentAutoSender.class), mock(PassportRetentionService.class), greske);
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
                mock(ConfirmationDocumentAutoSender.class), mock(PassportRetentionService.class), greske);

        scheduler.runDailyTasks();

        verify(greske, never()).record(any(), anyInt(), any());
    }
}
