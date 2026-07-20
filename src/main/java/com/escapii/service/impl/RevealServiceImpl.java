package com.escapii.service.impl;

import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.RevealEvent;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.RevealEventRepository;
import com.escapii.service.RevealService;
import com.escapii.service.email.ConfirmationDocumentEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevealServiceImpl implements RevealService {

    private final BookingRepository      bookingRepository;
    private final RevealEventRepository  revealEventRepository;
    private final ConfirmationDocumentEmailService confirmationDocumentEmailService;

    @Override
    public Map<String, Object> getRevealInfo(String token) {

        Booking booking = bookingRepository.findByRevealToken(token)
                .orElseThrow(() -> {
                    // Logujemo samo prvih 8 karaktera tokena - sprečava curenje punog tokena u logove
                    String safeToken = (token != null && token.length() > 8)
                            ? token.substring(0, 8) + "..." : token;
                    log.warn("[Reveal] Nepostojeci token: {}", safeToken);
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

        // Defense-in-depth: reveal se sme otkriti tek pošto je zvanično "otključan" (revealSentAt
        // postavljen od strane scheduler-a na T-2 ili ručno od strane admina). Trenutno se token
        // generiše tek pri slanju mejla, pa je ovo redundantno - ali sprečava buduću regresiju
        // ako bi se token ikad generisao ranije (npr. odmah pri potvrdi rezervacije).
        if (booking.getRevealSentAt() == null) {
            log.warn("[Reveal] Token postoji ali reveal još nije poslat za rezervaciju {}", booking.getBookingRef());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Destinacija još nije dostupna.");
        }

        // Link važi do dana polaska - nakon toga putovanje je počelo, link više nema smisla
        LocalDate departureDate = booking.getSelectedDate().getDepartureDate();
        if (LocalDate.now().isAfter(departureDate)) {
            log.warn("[Reveal] Token istekao za rezervaciju {} (polazak bio: {})",
                    booking.getBookingRef(), departureDate);
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Link je istekao - putovanje je već počelo. Srećan put! ✈");
        }

        // Izvuci imena svih putnika; ako lista prazna, koristi nosioca rezervacije
        List<String> passengerNames = booking.getPassengers() != null && !booking.getPassengers().isEmpty()
                ? booking.getPassengers().stream()
                         .map(com.escapii.model.PassengerInfo::getName)
                         .filter(n -> n != null && !n.isBlank())
                         .toList()
                : List.of(booking.getFirstName() + (booking.getLastName() != null ? " " + booking.getLastName() : ""));

        // Plaćeni dodaci (za popup)
        List<String> addons = getAddons(booking);

        return Map.ofEntries(
                Map.entry("destination",          booking.getAssignedDestination()),
                Map.entry("departureDate",        booking.getSelectedDate().getDepartureDate().toString()),
                Map.entry("returnDate",           booking.getSelectedDate().getReturnDate() != null
                                                      ? booking.getSelectedDate().getReturnDate().toString() : ""),
                Map.entry("bookingRef",           booking.getBookingRef()),
                Map.entry("departureAirport",     booking.getDepartureAirport() != null ? booking.getDepartureAirport() : ""),
                Map.entry("numberOfNights",       booking.getSelectedDate().getNumberOfNights()),
                Map.entry("passengers",           passengerNames),
                // Dodaci za popup detalje
                Map.entry("numberOfTravelers",    booking.getNumberOfTravelers() != null ? booking.getNumberOfTravelers() : 1),
                Map.entry("addons",               addons),
                Map.entry("totalPriceAll",        booking.getTotalPriceAll() != null ? booking.getTotalPriceAll() : 0),
                Map.entry("firstName",            booking.getFirstName() != null ? booking.getFirstName() : ""),
                Map.entry("airlineName",           booking.getAirlineName() != null ? booking.getAirlineName() : ""),
                Map.entry("airlineBookingCode",   booking.getAirlineBookingCode() != null ? booking.getAirlineBookingCode() : "")
        );
    }

    private static @NonNull List<String> getAddons(Booking booking) {
        List<String> addons = new java.util.ArrayList<>();
        if (Boolean.TRUE.equals(booking.getHasInsurance()))      addons.add("🛡 Putno osiguranje");
        if (Boolean.TRUE.equals(booking.getHasBreakfast()))      addons.add("🍳 Doručak");
        if (Boolean.TRUE.equals(booking.getHasSeatsTogether()))  addons.add("💺 Sedišta zajedno");
        if (Boolean.TRUE.equals(booking.getHasConnectingFlights())) addons.add("✈✈ Presedanje");
        if (booking.getCabinSuitcaseCount() != null && booking.getCabinSuitcaseCount() > 0) {
            addons.add("🧳 " + booking.getCabinSuitcaseCount() + "× kabinski kofer");
        }

        // Isključene destinacije
        List<String> excluded = new java.util.ArrayList<>();
        if (booking.getExcludedDestination1() != null) excluded.add(booking.getExcludedDestination1().getName());
        if (booking.getExcludedDestination2() != null) excluded.add(booking.getExcludedDestination2().getName());
        if (booking.getExcludedDestination3() != null) excluded.add(booking.getExcludedDestination3().getName());
        if (!excluded.isEmpty()) {
            addons.add("🚫 Isključeno: " + String.join(", ", excluded));
        }

        return addons;
    }

    @Override
    @Transactional
    public void confirmRevealed(String token) {
        bookingRepository.findByRevealToken(token).ifPresent(booking -> {
            // Idempotentno - samo prvi put, ne menjamo ako event već postoji
            if (revealEventRepository.findByBookingRef(booking.getBookingRef()).isEmpty()) {
                revealEventRepository.save(new RevealEvent(booking.getBookingRef()));
                log.info("[Reveal] Korisnik ogrebaо scratch karticu za rezervaciju {}", booking.getBookingRef());

                // Ako je admin VEĆ uploadovao dokument pre nego što je korisnik
                // pogledao reveal - šaljemo odmah (drugi mogući redosled od dva).
                if (booking.getConfirmationDocument() != null && booking.getConfirmationSentAt() == null) {
                    confirmationDocumentEmailService.sendConfirmationDocument(booking);
                    booking.setConfirmationSentAt(LocalDateTime.now());
                    bookingRepository.save(booking);
                    log.info("[ConfirmationDocument] Poslat za {} (dokument već postojao pre reveal-a)",
                            booking.getBookingRef());
                }
            }
        });
    }
}
