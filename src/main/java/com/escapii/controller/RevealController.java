package com.escapii.controller;

import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;

/**
 * Javni endpoint za otkrivanje destinacije.
 * NIJE zaštićen admin ključem — dostupan svima sa validnim tokenom.
 */
@Slf4j
@RestController
@RequestMapping("/api/reveal")
@RequiredArgsConstructor
public class RevealController {

    private final BookingRepository bookingRepository;

    /**
     * GET /api/reveal?token=abc123
     *
     * Validacije (sve mora proći):
     *  1. Token postoji u bazi
     *  2. Booking status == CONFIRMED
     *  3. assignedDestination nije null/prazan
     *  4. Datum polaska nije prošao (link važi do dana polaska)
     *
     * Vraća samo destinaciju i ime putnika — ništa osjetljivo.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> reveal(@RequestParam String token) {

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

        return ResponseEntity.ok(Map.of(
                "destination",      booking.getAssignedDestination(),
                "firstName",        booking.getFirstName(),
                "departureDate",    booking.getSelectedDate().getDepartureDate().toString(),
                "bookingRef",       booking.getBookingRef(),
                "departureAirport", booking.getDepartureAirport(),
                "numberOfNights",   booking.getSelectedDate().getNumberOfNights()
        ));
    }
}
