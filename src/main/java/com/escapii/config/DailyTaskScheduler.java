package com.escapii.config;

import com.escapii.model.Booking;
import com.escapii.repository.BookingRepository;
import com.escapii.service.email.DigestEmailService;
import com.escapii.service.email.ForecastEmailService;
import com.escapii.service.email.RevealEmailService;
import com.escapii.service.weather.DailyForecast;
import com.escapii.service.weather.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyTaskScheduler {

    private final BookingRepository  bookingRepository;
    private final DigestEmailService digestEmailService;
    private final RevealEmailService revealEmailService;
    private final ForecastEmailService forecastEmailService;
    private final WeatherService     weatherService;

    @Scheduled(cron = "0 0 10 * * *")
    public void runDailyTasks() {
        triggerDigest();
    }

    /** Ručno okidanje iz AdminController-a. */
    public void triggerDigest() {
        LocalDate today = LocalDate.now();

        // 1. Auto-reveal: CONFIRMED + destinacija + revealSentAt == null + polazak <= T+3
        List<Booking> revealReady = bookingRepository.findReadyForReveal(today.plusDays(3));
        List<Booking> revealSent  = sendReveals(revealReady);

        // 2. Auto-forecast: CONFIRMED + destinacija + forecastSentAt == null + polazak između T+4 i T+7
        //    Donji limit T+4 osigurava da se forecast NIKAD ne šalje posle reveal-a (koji ide na T+3)
        List<Booking> forecastReady = bookingRepository.findReadyForForecast(
                today.plusDays(4), today.plusDays(7));
        List<Booking> forecastSent  = sendForecasts(forecastReady);

        // 3. Sve CONFIRMED rezervacije u narednih 14 dana za digest preview
        List<Booking> upcoming = bookingRepository.findConfirmedDepartingBetween(today, today.plusDays(14));

        // 4. Digest email timu
        if (!upcoming.isEmpty() || !revealSent.isEmpty() || !forecastSent.isEmpty()) {
            digestEmailService.sendDailyDigest(today, revealSent, forecastSent, upcoming);
            log.info("[Scheduler] Digest poslan. Reveal: {}, Forecast: {}, Ukupno 14 dana: {}",
                    revealSent.size(), forecastSent.size(), upcoming.size());
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
     * Za svaki booking:
     *  1. Geocodira assignedDestination → lat/lon
     *  2. Preuzima 7-dnevnu prognozu sa Open-Meteo
     *  3. Šalje weather email korisniku (async)
     *  4. Setuje forecastSentAt
     *
     * Greška na jednom ne prekida ostale.
     */
    private List<Booking> sendForecasts(List<Booking> readyList) {
        List<Booking> sent = new ArrayList<>();

        for (Booking booking : readyList) {
            try {
                Optional<List<DailyForecast>> forecast =
                        weatherService.getForecast(booking.getAssignedDestination());

                if (forecast.isEmpty()) {
                    log.warn("[Forecast] ⚠️ Nije moguće preuzeti prognozu za '{}' ({})",
                            booking.getAssignedDestination(), booking.getBookingRef());
                    continue;
                }

                booking.setForecastSentAt(LocalDateTime.now());
                bookingRepository.save(booking);

                forecastEmailService.sendForecastEmail(booking, forecast.get());

                sent.add(booking);
                log.info("[Forecast] ✅ {} → dest='{}' dana={}",
                        booking.getBookingRef(),
                        booking.getAssignedDestination(),
                        forecast.get().size());
            } catch (Exception e) {
                log.error("[Forecast] ❌ Greška za {}: {}", booking.getBookingRef(), e.getMessage(), e);
            }
        }

        if (!sent.isEmpty()) {
            log.info("[Forecast] Ukupno poslato: {}/{}", sent.size(), readyList.size());
        }
        return sent;
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

    // ── Ručno slanje (admin dugmad) ───────────────────────────────────────────

    /**
     * POST /api/admin/bookings/{id}/send-reveal
     * Isti flow kao automatski, ali okida admin ručno.
     * Ako je već poslato → 409 Conflict (ne šalje ponovo).
     */
    /**
     * @param siteUrl WordPress sajt URL koji šalje request (npr. http://escapiitest.great-site.net
     *                 ili https://escapii.com). Koristi se za magic link u emailu.
     *                 Null = koristi konfigurisani app.frontend-url.
     */
    public Map<String, String> sendRevealForBooking(Long bookingId, String siteUrl) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Booking nije pronađen."));

        if (booking.getRevealSentAt() != null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Reveal je već poslan " + booking.getRevealSentAt() + ".");
        }
        if (booking.getAssignedDestination() == null || booking.getAssignedDestination().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Destinacija nije unesena — unesi je pre slanja reveal-a.");
        }

        if (booking.getRevealToken() == null) {
            booking.setRevealToken(UUID.randomUUID().toString().replace("-", ""));
        }
        booking.setRevealSentAt(LocalDateTime.now());
        bookingRepository.save(booking);
        revealEmailService.sendRevealEmail(booking, siteUrl); // prosleđuje URL sajta

        log.info("[Admin] ✉ Ručni reveal poslan za {} → '{}' (siteUrl={})",
                booking.getBookingRef(), booking.getAssignedDestination(), siteUrl);
        return Map.of("message", "Reveal email poslan za " + booking.getBookingRef() + ".");
    }

    /**
     * POST /api/admin/bookings/{id}/send-forecast
     * Isti flow kao automatski, ali okida admin ručno.
     * Ako je već poslato → 409 Conflict (ne šalje ponovo).
     */
    public Map<String, String> sendForecastForBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Booking nije pronađen."));

        if (booking.getForecastSentAt() != null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Prognoza je već poslata " + booking.getForecastSentAt() + ".");
        }
        if (booking.getAssignedDestination() == null || booking.getAssignedDestination().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Destinacija nije unesena — unesi je pre slanja prognoze.");
        }

        Optional<List<DailyForecast>> forecast =
                weatherService.getForecast(booking.getAssignedDestination());
        if (forecast.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Nije moguće preuzeti prognozu za '" + booking.getAssignedDestination() + "'.");
        }

        booking.setForecastSentAt(LocalDateTime.now());
        bookingRepository.save(booking);
        forecastEmailService.sendForecastEmail(booking, forecast.get());

        log.info("[Admin] 🌤 Ručna prognoza poslana za {} → '{}'",
                booking.getBookingRef(), booking.getAssignedDestination());
        return Map.of("message", "Prognoza email poslan za " + booking.getBookingRef() + ".");
    }
}
