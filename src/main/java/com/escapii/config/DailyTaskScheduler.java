package com.escapii.config;

import com.escapii.model.Booking;
import com.escapii.repository.BookingRepository;
import com.escapii.service.BookingSchedulingService;
import com.escapii.service.email.DigestEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyTaskScheduler {

    private final BookingSchedulingService schedulingService;
    private final BookingRepository        bookingRepository;
    private final DigestEmailService       digestEmailService;

    @Scheduled(cron = "0 0 7 * * *", zone = "Europe/Belgrade")
    public void runDailyTasks() {
        schedulingService.sendPendingReveals();
        schedulingService.sendPendingForecasts();
        schedulingService.cancelStalePendingBookings();
        sendDigest();
    }

    /** Ručno okidanje iz AdminController-a — okida iste taskove kao i automatski. */
    public void triggerDigest() {
        schedulingService.sendPendingReveals();
        schedulingService.sendPendingForecasts();
        schedulingService.cancelStalePendingBookings();
        sendDigest();
    }

    public Map<String, String> sendRevealForBooking(Long id, String url) {
        return schedulingService.sendRevealForBooking(id, url);
    }

    public Map<String, String> sendForecastForBooking(Long id) {
        return schedulingService.sendForecastForBooking(id);
    }

    // ── Digest helper ─────────────────────────────────────────────────────────

    private void sendDigest() {
        LocalDate today = LocalDate.now();

        // Bookings whose reveal was sent today
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay   = today.plusDays(1).atStartOfDay();
        List<Booking> revealSent   = bookingRepository.findRevealSentBetween(startOfDay, endOfDay);
        List<Booking> forecastSent = bookingRepository.findForecastSentBetween(startOfDay, endOfDay);
        List<Booking> upcoming     = bookingRepository.findConfirmedDepartingBetween(today, today.plusDays(14));

        if (!upcoming.isEmpty() || !revealSent.isEmpty() || !forecastSent.isEmpty()) {
            digestEmailService.sendDailyDigest(today, revealSent, forecastSent, upcoming);
            log.info("[Scheduler] Digest poslan. Reveal: {}, Forecast: {}, Ukupno 14 dana: {}",
                    revealSent.size(), forecastSent.size(), upcoming.size());
        } else {
            log.info("[Scheduler] Nema aktivnih rezervacija — digest nije poslan.");
        }
    }
}
