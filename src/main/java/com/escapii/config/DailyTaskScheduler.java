package com.escapii.config;

import com.escapii.model.Booking;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyTaskScheduler {

    private final BookingRepository  bookingRepository;
    private final DigestEmailService digestEmailService;
    private final RevealEmailService revealEmailService;

    @Scheduled(cron = "0 0 10 * * *")
    public void runDailyTasks() {
        triggerDigest();
    }

    /** Ručno okidanje iz AdminController-a. */
    public void triggerDigest() {
        LocalDate today = LocalDate.now();

        // 1. Auto-reveal: CONFIRMED + destinacija dodeljena + revealSentAt == null + polazak <= T+3
        List<Booking> revealReady = bookingRepository.findReadyForReveal(today.plusDays(3));
        List<Booking> revealSent  = sendReveals(revealReady);

        // 2. Sve CONFIRMED rezervacije u narednih 14 dana za digest preview
        List<Booking> upcoming = bookingRepository.findConfirmedDepartingBetween(today, today.plusDays(14));

        // 3. Prognoza danas (T+5) — za sada informativno u digestu, auto-slanje dolazi kasnije
        LocalDate forecastDate = today.plusDays(5);
        List<Booking> forecastDue = upcoming.stream()
                .filter(b -> b.getSelectedDate().getDepartureDate().equals(forecastDate))
                .toList();

        // 4. Digest email timu
        if (!upcoming.isEmpty() || !revealSent.isEmpty()) {
            digestEmailService.sendDailyDigest(today, revealSent, forecastDue, upcoming);
            log.info("[Scheduler] Digest poslan. Reveal-ovi danas: {}, forecast danas: {}, ukupno u 14 dana: {}",
                    revealSent.size(), forecastDue.size(), upcoming.size());
        } else {
            log.info("[Scheduler] Nema aktivnih rezervacija — digest nije poslan.");
        }
    }

    /** Auto-cancel PENDING rezervacija starijih od 5 dana. */
    @Scheduled(cron = "0 5 10 * * *")
    @Transactional
    public void autoCancelStalePending() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(5);
        List<Booking> stale = bookingRepository.findStalePendingBefore(cutoff);

        if (stale.isEmpty()) {
            log.info("[Scheduler] Auto-cancel: nema PENDING rezervacija starijih od 5 dana.");
            return;
        }
        for (Booking b : stale) {
            b.setStatus(com.escapii.model.BookingStatus.CANCELLED);
            bookingRepository.save(b);
            log.info("[Scheduler] Auto-cancel: {} otkazan (kreiran: {})", b.getBookingRef(), b.getCreatedAt());
        }
        log.info("[Scheduler] Auto-cancel završen — otkazano {} rezervacija.", stale.size());
    }

    /**
     * Za svaki booking iz liste:
     *  1. Generiši revealToken ako nedostaje
     *  2. Sačuvaj token u bazu
     *  3. Označi revealSentAt (pre slanja — da ne bi duplo slali pri ponovnom pokušaju)
     *  4. Sačuvaj u bazu
     *  5. Pošalji email korisniku (async)
     *
     * Greška na jednom booking-u ne prekida ostale.
     */
    private List<Booking> sendReveals(List<Booking> readyList) {
        List<Booking> sent = new ArrayList<>();

        for (Booking booking : readyList) {
            try {
                if (booking.getRevealToken() == null) {
                    booking.setRevealToken(UUID.randomUUID().toString().replace("-", ""));
                }
                booking.setRevealSentAt(LocalDateTime.now());
                bookingRepository.save(booking);

                revealEmailService.sendRevealEmail(booking);

                sent.add(booking);
                log.info("[Reveal] ✅ {} → {}", booking.getBookingRef(), booking.getAssignedDestination());
            } catch (Exception e) {
                log.error("[Reveal] ❌ Greška za {}: {}", booking.getBookingRef(), e.getMessage(), e);
            }
        }

        if (!sent.isEmpty()) {
            log.info("[Reveal] Ukupno poslato: {}/{}", sent.size(), readyList.size());
        }
        return sent;
    }
}
