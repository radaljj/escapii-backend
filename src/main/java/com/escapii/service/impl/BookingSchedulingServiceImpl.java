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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingSchedulingServiceImpl implements BookingSchedulingService {

    private final BookingRepository   bookingRepository;
    private final RevealEmailService  revealEmailService;
    private final ForecastEmailService forecastEmailService;
    private final WeatherService      weatherService;

    @Override
    @Transactional
    public void sendPendingReveals() {
        LocalDate today = LocalDate.now();
        List<Booking> readyList = bookingRepository.findReadyForReveal(today.plusDays(3));
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
            log.info("[Scheduler] Auto-cancel: nema PENDING rezervacija starijih od 5 dana.");
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
    public Map<String, String> sendRevealForBooking(Long bookingId, String siteUrl) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking nije pronađen."));

        if (booking.getRevealSentAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Reveal je već poslan " + booking.getRevealSentAt() + ".");
        }
        if (booking.getAssignedDestination() == null || booking.getAssignedDestination().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Destinacija nije unesena — unesi je pre slanja reveal-a.");
        }

        if (booking.getRevealToken() == null) {
            booking.setRevealToken(TokenUtils.generate());
        }
        booking.setRevealSentAt(LocalDateTime.now());
        bookingRepository.save(booking);
        revealEmailService.sendRevealEmail(booking, siteUrl);

        log.info("[Admin] Ručni reveal poslan za {} → '{}' (siteUrl={})",
                booking.getBookingRef(), booking.getAssignedDestination(), siteUrl);
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
        if (booking.getAssignedDestination() == null || booking.getAssignedDestination().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Destinacija nije unesena — unesi je pre slanja prognoze.");
        }

        Optional<List<DailyForecast>> forecast =
                weatherService.getForecast(booking.getAssignedDestination());
        if (forecast.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Nije moguće preuzeti prognozu za '" + booking.getAssignedDestination() + "'.");
        }

        booking.setForecastSentAt(LocalDateTime.now());
        bookingRepository.save(booking);
        forecastEmailService.sendForecastEmail(booking, forecast.get());

        log.info("[Admin] Ručna prognoza poslana za {} → '{}'",
                booking.getBookingRef(), booking.getAssignedDestination());
        return Map.of("message", "Prognoza email poslan za " + booking.getBookingRef() + ".");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<Booking> sendForecasts(List<Booking> readyList) {
        List<Booking> sent = new ArrayList<>();

        for (Booking booking : readyList) {
            try {
                Optional<List<DailyForecast>> forecast =
                        weatherService.getForecast(booking.getAssignedDestination());

                if (forecast.isEmpty()) {
                    log.warn("[Forecast] Nije moguće preuzeti prognozu za '{}' ({})",
                            booking.getAssignedDestination(), booking.getBookingRef());
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
}
