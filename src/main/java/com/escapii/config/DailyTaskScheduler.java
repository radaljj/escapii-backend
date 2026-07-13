package com.escapii.config;

import com.escapii.model.Booking;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.CustomDateInquiryRepository;
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

    private final BookingSchedulingService     schedulingService;
    private final BookingRepository            bookingRepository;
    private final DigestEmailService           digestEmailService;
    private final AvailableDateRepository      availableDateRepository;
    private final CustomDateInquiryRepository  inquiryRepository;

    @Scheduled(cron = "0 0 10 * * *", zone = "Europe/Belgrade")
    public void runDailyTasks() {
        schedulingService.sendPendingReveals();
        schedulingService.sendPendingForecasts();
        schedulingService.cancelStalePendingBookings();
        schedulingService.completeFinishedBookings();
        sendDigest();
        cleanupExpiredDates();
        cleanupClosedInquiries();
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /**
     * Briše termine čiji je datum polaska prošao:
     * - bez rezervacija → briše se iz baze
     * - sa rezervacijama → deaktivira se (čuva istoriju)
     */
    public void cleanupExpiredDates() {
        LocalDate today = LocalDate.now();
        int deleted     = availableDateRepository.deleteExpiredWithNoBookings(today);
        int deactivated = availableDateRepository.deactivateExpiredWithBookings(today);
        if (deleted > 0 || deactivated > 0) {
            log.info("[Cleanup] Termini: obrisano={}, deaktivirano={}", deleted, deactivated);
        }
    }

    /**
     * Briše zatvorene upite (status=CLOSED) koji su zatvoreni pre više od 24 sata.
     */
    public void cleanupClosedInquiries() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        int deleted = inquiryRepository.deleteClosedBefore(cutoff);
        if (deleted > 0) {
            log.info("[Cleanup] Upiti: obrisano {} zatvorenih upita starijih od 24h", deleted);
        }
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

        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay   = today.plusDays(1).atStartOfDay();
        List<Booking> revealSent        = bookingRepository.findRevealSentBetween(startOfDay, endOfDay);
        List<Booking> forecastSent      = bookingRepository.findForecastSentBetween(startOfDay, endOfDay);
        List<Booking> upcoming          = bookingRepository.findConfirmedDepartingBetween(today, today.plusDays(14));
        // Reveal Box podsetnik - polazak od danas do +5 dana
        List<Booking> revealBoxPending  = bookingRepository.findPendingRevealBoxes(today, today.plusDays(5));
        // Korisnik otvorio reveal stranicu - tim treba da pošalje potvrdu leta/smeštaja
        List<Booking> revealedAndViewed = bookingRepository.findRevealedAndViewed(today, today.plusDays(14));
        // Korisnik NIJE otvorio reveal, a polazak je za <= 2 dana - hitno upozorenje
        List<Booking> notViewedUrgent   = bookingRepository.findRevealedButNotViewed(today, today.plusDays(2));

        if (!upcoming.isEmpty() || !revealSent.isEmpty() || !forecastSent.isEmpty()
                || !revealBoxPending.isEmpty() || !revealedAndViewed.isEmpty() || !notViewedUrgent.isEmpty()) {
            digestEmailService.sendDailyDigest(today, revealSent, forecastSent, upcoming,
                    revealBoxPending, revealedAndViewed, notViewedUrgent);
            log.info("[Scheduler] Digest poslan. Reveal: {}, Forecast: {}, Ukupno 14 dana: {}, RevealBox: {}, Viewed: {}, NotViewed urgent: {}",
                    revealSent.size(), forecastSent.size(), upcoming.size(),
                    revealBoxPending.size(), revealedAndViewed.size(), notViewedUrgent.size());
        } else {
            log.info("[Scheduler] Nema aktivnih rezervacija - digest nije poslan.");
        }
    }
}
