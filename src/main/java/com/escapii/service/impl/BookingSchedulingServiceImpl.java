package com.escapii.service.impl;

import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.service.BookingSchedulingService;
import com.escapii.service.email.ForecastEmailService;
import com.escapii.service.email.RevealEmailService;
import com.escapii.service.weather.DailyForecast;
import com.escapii.service.weather.WeatherService;
import com.escapii.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingSchedulingServiceImpl implements BookingSchedulingService {

    private final BookingRepository    bookingRepository;
    private final RevealEmailService   revealEmailService;
    private final ForecastEmailService forecastEmailService;
    private final WeatherService       weatherService;

    @Value("${app.cors-allowed-origin:https://escapii.rs}")
    private String corsAllowedOrigin;

    @Value("${app.cors-extra-origins:}")
    private String corsExtraOrigins;

    @Value("${app.frontend-url:https://escapii.rs}")
    private String defaultFrontendUrl;

    @Override
    @Transactional
    public void sendPendingReveals() {
        LocalDate today = LocalDate.now();
        List<Booking> readyList = bookingRepository.findReadyForReveal(today.plusDays(2));
        sendReveals(readyList);
    }

    @Override
    @Transactional
    public void sendPendingForecasts() {
        LocalDate today = LocalDate.now();
        List<Booking> readyList = bookingRepository.findReadyForForecast(
                today.plusDays(4), today.plusDays(7));
        sendForecasts(readyList);
    }

    @Override
    @Transactional
    public void cancelStalePendingBookings() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(5);
        List<Booking> stale = bookingRepository.findStalePendingBefore(cutoff);

        if (stale.isEmpty()) {
            return;
        }
        for (Booking b : stale) {
            b.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(b);
            log.info("[Scheduler] Auto-cancel: {} otkazan (kreiran: {})", b.getBookingRef(), b.getCreatedAt());
        }
        log.info("[Scheduler] Auto-cancel završen — otkazano {} rezervacija.", stale.size());
    }

    @Override
    @Transactional
    public void completeFinishedBookings() {
        LocalDate today = LocalDate.now();
        List<Booking> ready = bookingRepository.findReadyForCompletion(today);

        if (ready.isEmpty()) {
            return;
        }
        for (Booking b : ready) {
            b.setStatus(BookingStatus.COMPLETED);
            bookingRepository.save(b);
        }
        log.info("[Scheduler] Auto-complete završen — zatvoreno {} rezervacija.", ready.size());
    }

    @Override
    @Transactional
    public Map<String, String> sendRevealForBooking(Long bookingId, String siteUrl) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking nije pronađen."));

        if (booking.getRevealSentAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Reveal je već poslan " + booking.getRevealSentAt() + ".");
        }
        validateIsAssignedDestination(booking.getAssignedDestination());


        // Validiraj X-Frontend-Url da nije open redirect — mora biti u dozvoljenim originima
        String validatedUrl = validateFrontendUrl(siteUrl);

        if (booking.getRevealToken() == null) {
            booking.setRevealToken(TokenUtils.generate());
        }
        booking.setRevealSentAt(LocalDateTime.now());
        bookingRepository.save(booking);
        revealEmailService.sendRevealEmail(booking, validatedUrl);

        log.info("[Admin] Ručni reveal poslan za {} → '{}' (siteUrl={})",
                booking.getBookingRef(), booking.getAssignedDestination(), validatedUrl);
        return Map.of("message", "Reveal email poslan za " + booking.getBookingRef() + ".");
    }

    @Override
    @Transactional
    public Map<String, String> sendForecastForBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking nije pronađen."));

        if (booking.getForecastSentAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Prognoza je već poslata " + booking.getForecastSentAt() + ".");
        }
        validateIsAssignedDestination(booking.getAssignedDestination());

        String weatherQuery = resolveWeatherQuery(booking);
        Optional<List<DailyForecast>> forecast = weatherService.getForecast(weatherQuery);
        if (forecast.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Nije moguće preuzeti prognozu za '" + weatherQuery + "'. " +
                    "Pokušaj uneti precizniji naziv u polje 'Grad za prognozu'.");
        }

        booking.setForecastSentAt(LocalDateTime.now());
        bookingRepository.save(booking);
        forecastEmailService.sendForecastEmail(booking, forecast.get());

        log.info("[Admin] Ručna prognoza poslana za {} → '{}' (weatherQuery='{}')",
                booking.getBookingRef(), booking.getAssignedDestination(), weatherQuery);
        return Map.of("message", "Prognoza email poslan za " + booking.getBookingRef() + ".");
    }

    // ── Weather helpers ───────────────────────────────────────────────────────

    /**
     * Vraća string koji se šalje geocoderu za vremensku prognozu.
     * Admin može uneti precizniji naziv u weatherCity polje (npr. "Santa Cruz de Tenerife, Spain")
     * dok assignedDestination ostaje marketinški naziv koji vidi korisnik (npr. "Tenerife").
     */
    private String resolveWeatherQuery(Booking booking) {
        String wc = booking.getWeatherCity();
        return (wc != null && !wc.isBlank()) ? wc.strip() : booking.getAssignedDestination();
    }

    // ── Security helpers ──────────────────────────────────────────────────────

    /**
     * Validira X-Frontend-Url header da sprečava open redirect napad.
     * Prihvata URL samo ako tačno odgovara jednom od dozvoljenih CORS origina.
     * Ako ne odgovara → vraća konfigurisani defaultFrontendUrl.
     */
    private String validateFrontendUrl(String siteUrl) {
        if (siteUrl == null || siteUrl.isBlank()) {
            return null; // RevealEmailServiceImpl će koristiti konfigurisani frontendUrl
        }

        Set<String> allowed = buildAllowedOrigins();
        String normalized = siteUrl.stripTrailing().replaceAll("/+$", "");

        if (allowed.contains(normalized)) {
            return normalized;
        }

        log.warn("[Security] X-Frontend-Url '{}' nije u dozvoljenim originima — koristi se default", siteUrl);
        return defaultFrontendUrl;
    }

    private Set<String> buildAllowedOrigins() {
        Set<String> origins = new java.util.HashSet<>();
        if (corsAllowedOrigin != null && !corsAllowedOrigin.isBlank()) {
            Arrays.stream(corsAllowedOrigin.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(origins::add);
        }
        if (corsExtraOrigins != null && !corsExtraOrigins.isBlank()) {
            Arrays.stream(corsExtraOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(origins::add);
        }
        return origins;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<Booking> sendForecasts(List<Booking> readyList) {
        List<Booking> sent = new ArrayList<>();

        for (Booking booking : readyList) {
            try {
                String weatherQuery = resolveWeatherQuery(booking);
                Optional<List<DailyForecast>> forecast = weatherService.getForecast(weatherQuery);

                if (forecast.isEmpty()) {
                    log.warn("[Forecast] Nije moguće preuzeti prognozu za '{}' weatherQuery='{}' ({})",
                            booking.getAssignedDestination(), weatherQuery, booking.getBookingRef());
                    continue;
                }

                booking.setForecastSentAt(LocalDateTime.now());
                bookingRepository.save(booking);

                forecastEmailService.sendForecastEmail(booking, forecast.get());

                sent.add(booking);
                log.info("[Forecast] {} → dest='{}' dana={}",
                        booking.getBookingRef(),
                        booking.getAssignedDestination(),
                        forecast.get().size());
            } catch (Exception e) {
                log.error("[Forecast] Greška za {}: {}", booking.getBookingRef(), e.getMessage(), e);
            }
        }

        if (!sent.isEmpty()) {
            log.info("[Forecast] Ukupno poslato: {}/{}", sent.size(), readyList.size());
        }
        return sent;
    }

    private List<Booking> sendReveals(List<Booking> readyList) {
        List<Booking> sent = new ArrayList<>();

        for (Booking booking : readyList) {
            try {
                if (booking.getRevealToken() == null) {
                    booking.setRevealToken(TokenUtils.generate());
                }
                booking.setRevealSentAt(LocalDateTime.now());
                bookingRepository.save(booking);

                revealEmailService.sendRevealEmail(booking);

                sent.add(booking);
                log.info("[Reveal] {} → {}", booking.getBookingRef(), booking.getAssignedDestination());
            } catch (Exception e) {
                log.error("[Reveal] Greška za {}: {}", booking.getBookingRef(), e.getMessage(), e);
            }
        }

        if (!sent.isEmpty()) {
            log.info("[Reveal] Ukupno poslato: {}/{}", sent.size(), readyList.size());
        }
        return sent;
    }

    private void validateIsAssignedDestination(String assignedDestination) {
        if (assignedDestination == null || assignedDestination.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Destinacija nije unesena — unesi je pre slanja prognoze.");
        }
    }
}
