package com.escapii.config;

import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Svako jutro u 08:00 (po server vremenu) šalje operativni digest na ops-email.
 *
 * Logika po rezervaciji:
 *   weatherSent=false  + polazak ≤ 7 dana → hitno/danas
 *   destinationSent=false + polazak ≤ 3 dana → hitno/danas
 *   ostalo → preview sekcija "narednih 14 dana"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyTaskScheduler {

    private final BookingRepository bookingRepository;
    private final EmailService      emailService;

    /** Svaki dan u 08:00. Cron: sekunda minuta sat dan mesec danUNedelji */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendMorningDigest() {
        triggerDigest();
    }

    /** Svaki dan u 08:05 — auto-cancel PENDING rezervacija starijih od 5 dana. */
    @Scheduled(cron = "0 5 8 * * *")
    @Transactional
    public void autoCancelStalePending() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(5);
        List<Booking> stale = bookingRepository.findStalePendingBefore(cutoff);

        if (stale.isEmpty()) {
            log.info("[Scheduler] Auto-cancel: nema PENDING rezervacija starijih od 5 dana.");
            return;
        }

        for (Booking booking : stale) {
            booking.setOldStatus(BookingStatus.PENDING);
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);
            log.info("[Scheduler] Auto-cancel: rezervacija {} otkazana (kreirana: {}, ref: {})",
                    booking.getId(), booking.getCreatedAt(), booking.getBookingRef());
        }

        log.info("[Scheduler] Auto-cancel završen — otkazano {} rezervacija.", stale.size());
    }

    /** Ručno okidanje za testiranje — poziva se iz AdminController-a. */
    public void triggerDigest() {
        LocalDate today = LocalDate.now();
        List<Booking> upcoming = bookingRepository.findConfirmedDepartingBetween(today, today.plusDays(14));

        if (upcoming.isEmpty()) {
            log.info("[Scheduler] Nema nadolazećih CONFIRMED rezervacija — digest nije poslat.");
            return;
        }

        emailService.sendDailyDigest(today, upcoming);
        log.info("[Scheduler] Jutarnji digest poslat za {} rezervacija.", upcoming.size());
    }
}
