package com.escapii.service.impl;

import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.service.RevealService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevealServiceImpl implements RevealService {

    private final BookingRepository bookingRepository;

    @Override
    public Map<String, Object> getRevealInfo(String token) {

        Booking booking = bookingRepository.findByRevealToken(token)
                .orElseThrow(() -> {
                    log.warn("[Reveal] Nepostojeci token: {}", token);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Link nije validan.");
                });

        // Mora biti potvrđena rezervacija
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            log.warn("[Reveal] Rezervacija {} nije CONFIRMED (status={})",
                    booking.getBookingRef(), booking.getStatus());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Rezervacija nije potvrđena.");
        }

        // Destinacija mora biti unesena
        if (booking.getAssignedDestination() == null || booking.getAssignedDestination().isBlank()) {
            log.warn("[Reveal] Destinacija nije unesena za rezervaciju {}", booking.getBookingRef());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Destinacija još nije dostupna.");
        }

        // Link važi do dana polaska — nakon toga putovanje je počelo, link više nema smisla
        LocalDate departureDate = booking.getSelectedDate().getDepartureDate();
        if (LocalDate.now().isAfter(departureDate)) {
            log.warn("[Reveal] Token istekao za rezervaciju {} (polazak bio: {})",
                    booking.getBookingRef(), departureDate);
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Link je istekao — putovanje je već počelo. Srećan put! ✈");
        }

        log.info("[Reveal] Destinacija otkrivena za rezervaciju {}", booking.getBookingRef());

        // Izvuci imena svih putnika; ako lista prazna, koristi nosioca rezervacije
        List<String> passengerNames = booking.getPassengers() != null && !booking.getPassengers().isEmpty()
                ? booking.getPassengers().stream()
                         .map(com.escapii.model.PassengerInfo::getName)
                         .filter(n -> n != null && !n.isBlank())
                         .collect(Collectors.toList())
                : List.of(booking.getFirstName() + (booking.getLastName() != null ? " " + booking.getLastName() : ""));

        return Map.ofEntries(
                Map.entry("destination",      booking.getAssignedDestination()),
                Map.entry("departureDate",    booking.getSelectedDate().getDepartureDate().toString()),
                Map.entry("returnDate",       booking.getSelectedDate().getReturnDate() != null
                                                  ? booking.getSelectedDate().getReturnDate().toString() : ""),
                Map.entry("bookingRef",       booking.getBookingRef()),
                Map.entry("departureAirport", booking.getDepartureAirport() != null ? booking.getDepartureAirport() : ""),
                Map.entry("numberOfNights",   booking.getSelectedDate().getNumberOfNights()),
                Map.entry("passengers",       passengerNames)
        );
    }
}
