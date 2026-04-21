package com.escapii.config;

import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.service.email.DigestEmailService;
import com.escapii.service.email.RevealEmailService;
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

    private final BookingRepository  bookingRepository;
    private final DigestEmailService digestEmailService;
    private final RevealEmailService revealEmailService;

    /** Svaki dan u 10:00. Cron: sekunda minuta sat dan mesec danUNedelji */
    @Scheduled(cron = "0 0 10 * * *")
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
    @Transactional
    public void triggerDigest() {
        LocalDate today = LocalDate.now();

        // ── 1. Reveal: pošalji destinacije za bookinge koji su T-3 ili ranije (catch-up) ──
        processRevealEmails(today);

        // ── 2. Jutarnji digest ────────────────────────────────────────────────────────────
        List<Booking> upcoming = bookingRepository.findConfirmedDepartingBetween(today, today.plusDays(14));
        if (upcoming.isEmpty()) {
            log.info("[Scheduler] Nema nadolazećih CONFIRMED rezervacija — digest nije poslat.");
            return;
        }
        digestEmailService.sendDailyDigest(today, upcoming);
        log.info("[Scheduler] Jutarnji digest poslat za {} rezervacija.", upcoming.size());
    }

    /**
     * Pronađi sve CONFIRMED bookinge koji:
     *  - imaju assignedDestination
     *  - reveal još nije poslan (revealSentAt == null)
     *  - polazak je za <= 3 dana (ili je već prošlo — catch-up slučaj)
     * Pošalji reveal email i označi kao poslato.
     */
    private void processRevealEmails(LocalDate today) {
        LocalDate cutoff = today.plusDays(3);
        List<Booking> readyList = bookingRepository.findReadyForReveal(cutoff);

        if (readyList.isEmpty()) {
            log.info("[Reveal] Nema booking-a spremnih za reveal danas.");
            return;
        }

        for (Booking booking : readyList) {
            try {
                revealEmailService.sendRevealEmail(booking);
                booking.setRevealSentAt(LocalDateTime.now());
                bookingRepository.save(booking);
                log.info("[Reveal] ✅ Poslan za {} ({})", booking.getBookingRef(),
                        booking.getAssignedDestination());
            } catch (Exception e) {
                // Ne prekidaj ostatak — loguj grešku i nastavi sa sledećim
                log.error("[Reveal] ❌ Greška pri slanju za {}: {}",
                        booking.getBookingRef(), e.getMessage(), e);
            }
        }

        log.info("[Reveal] Ukupno obrađeno: {} booking-a.", readyList.size());
    }
}
