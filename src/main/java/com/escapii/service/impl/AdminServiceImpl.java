package com.escapii.service.impl;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.AdminDateRequest;
import com.escapii.dto.AdminDateResponse;
import com.escapii.dto.DestinationResponse;
import com.escapii.mapper.AdminBookingMapper;
import com.escapii.mapper.DestinationMapper;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.Destination;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.DestinationRepository;
import com.escapii.service.AdminService;
import com.escapii.service.email.BookingEmailService;
import com.escapii.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AvailableDateRepository availableDateRepository;
    private final DestinationRepository   destinationRepository;
    private final BookingRepository       bookingRepository;
    private final AdminBookingMapper      adminBookingMapper;
    private final DestinationMapper       destinationMapper;
    private final BookingEmailService     bookingEmailService;
    private final WaitlistService         waitlistService;

    // ══ DESTINACIJE ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<DestinationResponse> getAllDestinations() {
        return destinationMapper.toResponseList(destinationRepository.findAllByOrderByNameAsc());
    }

    @Override
    @Transactional
    public void toggleDestinationActive(Long id, boolean active) {
        Destination dest = destinationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Destinacija ne postoji: " + id));
        dest.setActive(active);
        destinationRepository.save(dest);
        log.info("[ADMIN] Destinacija '{}' (id={}) {}", dest.getName(), id,
                active ? "aktivirana" : "deaktivirana");
    }

    // ══ TERMINI ══════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<AdminDateResponse> getAllDates() {
        return availableDateRepository.findAllByOrderByDepartureDateAsc()
                .stream()
                .map(AdminDateResponse::new)
                .toList();
    }

    @Override
    @Transactional
    public AdminDateResponse addDate(AdminDateRequest req) {
        if (!req.getReturnDate().isAfter(req.getDepartureDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Datum povratka mora biti posle datuma polaska");
        }

        // Izračunaj numberOfNights automatski — ne primamo od klijenta da ne bi bilo nekonzistentnosti
        int calculatedNights = (int) java.time.temporal.ChronoUnit.DAYS.between(
                req.getDepartureDate(), req.getReturnDate());

        AvailableDate date = new AvailableDate();
        date.setDepartureDate(req.getDepartureDate());
        date.setReturnDate(req.getReturnDate());
        date.setNumberOfNights(calculatedNights);
        date.setDepartureAirport(req.getDepartureAirport().toUpperCase());
        date.setAvailableSlots(req.getAvailableSlots());
        date.setBasePrice(req.getBasePrice());
        date.setActive(true);

        if (req.getPotentialDestinationIds() != null && !req.getPotentialDestinationIds().isEmpty()) {
            List<Destination> destinations = destinationRepository.findAllById(req.getPotentialDestinationIds());
            date.setPotentialDestinations(destinations);
        }

        AvailableDate saved = availableDateRepository.save(date);
        log.info("[ADMIN] Dodat termin id={} | {} → {} | aerodrom={} | destinacije={}",
                saved.getId(), saved.getDepartureDate(), saved.getReturnDate(),
                saved.getDepartureAirport(),
                saved.getPotentialDestinations().stream().map(Destination::getName).toList());

        // Automatski notifikuj waitlist korisnike za ovaj aerodrom
//        int notified = waitlistService.notifyAndClear(saved.getDepartureAirport());
//        if (notified > 0) {
//            log.info("[ADMIN] Waitlist notifikacija poslata za {} korisnika (aerodrom={})",
//                    notified, saved.getDepartureAirport());
//        }

        return new AdminDateResponse(saved);
    }

    @Override
    @Transactional
    public AdminDateResponse updateDestinations(Long id, List<Long> destinationIds) {
        AvailableDate date = findDateOrThrow(id);
        List<Destination> destinations = destinationRepository.findAllById(destinationIds);
        date.setPotentialDestinations(destinations);
        AvailableDate saved = availableDateRepository.save(date);
        log.info("[ADMIN] Azurirane destinacije za termin id={} → {}",
                id, destinations.stream().map(Destination::getName).toList());
        return new AdminDateResponse(saved);
    }

    @Override
    @Transactional
    public void toggleActive(Long id, boolean active) {
        AvailableDate date = findDateOrThrow(id);
        date.setActive(active);
        availableDateRepository.save(date);
        log.info("[ADMIN] Termin id={} {} | {} → {}",
                id, active ? "aktiviran" : "deaktiviran",
                date.getDepartureDate(), date.getReturnDate());
    }

    @Override
    @Transactional
    public void updateSlots(Long id, int slots) {
        if (slots < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Broj mesta ne može biti negativan");
        }
        AvailableDate date = findDateOrThrow(id);
        int oldSlots = date.getAvailableSlots();
        date.setAvailableSlots(slots);
        availableDateRepository.save(date);
        log.info("[ADMIN] Termin id={} | mesta: {} → {}", id, oldSlots, slots);
    }

    @Override
    @Transactional
    public void deleteDate(Long id) {
        AvailableDate date = findDateOrThrow(id);
        availableDateRepository.deleteById(id);
        log.info("[ADMIN] Obrisan termin id={} | {} → {} | aerodrom={}",
                id, date.getDepartureDate(), date.getReturnDate(), date.getDepartureAirport());
    }

    // ══ REZERVACIJE ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<AdminBookingResponse> getAllBookings() {
        return adminBookingMapper.toResponseList(bookingRepository.findAllByOrderByCreatedAtDesc());
    }

    @Override
    @Transactional
    public AdminBookingResponse updateBookingStatus(Long id, BookingStatus status) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija ne postoji: " + id));

        BookingStatus oldStatus = booking.getStatus();
        booking.setOldStatus(oldStatus);
        booking.setStatus(status);
        Booking saved = bookingRepository.save(booking);

        log.info("[ADMIN] Rezervacija {} | status: {} → {}",
                saved.getBookingRef(), oldStatus, status);

        Integer numberOfTravelers = booking.getNumberOfTravelers();
        BookingStatus bookingStatus = booking.getStatus();

        updateAvailableSlotsForSelectedDate(id, numberOfTravelers, bookingStatus, oldStatus);

        // Slanje emaila korisniku na osnovu novog statusa
        if (status == BookingStatus.CONFIRMED) {
            bookingEmailService.sendBookingConfirmed(saved);
        } else if (status == BookingStatus.CANCELLED && oldStatus == BookingStatus.CONFIRMED) {
            bookingEmailService.sendBookingCancelled(saved);
        }

        return adminBookingMapper.toResponse(saved);
    }

    private void updateAvailableSlotsForSelectedDate(Long bookingId, Integer numberOfTravelers,
                                                       BookingStatus newStatus, BookingStatus oldStatus) {
        if (newStatus == oldStatus) return;

        AvailableDate date = availableDateRepository
                .findByBookingId(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Nema pronadjenog datuma za booking id: " + bookingId));

        int delta = 0;

        if (newStatus == BookingStatus.CONFIRMED && oldStatus != BookingStatus.CONFIRMED) {
            // PENDING→CONFIRMED ili CANCELLED→CONFIRMED: zauzmi mesta
            delta = -numberOfTravelers;
        } else if (oldStatus == BookingStatus.CONFIRMED && newStatus != BookingStatus.CONFIRMED) {
            // CONFIRMED→PENDING ili CONFIRMED→CANCELLED: oslobodi mesta
            delta = +numberOfTravelers;
        }

        if (delta == 0) return;

        int newSlots = date.getAvailableSlots() + delta;
        if (newSlots < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Nema dovoljno slobodnih mesta za potvrdu rezervacije (dostupno: "
                    + date.getAvailableSlots() + ", potrebno: " + numberOfTravelers + ")");
        }

        date.setAvailableSlots(newSlots);
        availableDateRepository.save(date);

        log.info("[ADMIN] Slotovi termina id={} ažurirani: {} → {} (delta={}, booking={})",
                date.getId(), date.getAvailableSlots() - delta, newSlots, delta, bookingId);
    }

    @Override
    @Transactional
    public AdminBookingResponse updateAdminNotes(Long id, String adminNotes) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija ne postoji: " + id));
        booking.setAdminNotes(adminNotes == null ? null : adminNotes.strip());
        Booking saved = bookingRepository.save(booking);
        log.info("[ADMIN] Napomena ažurirana za rezervaciju {}", saved.getBookingRef());
        return adminBookingMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AdminBookingResponse setDestination(Long id, String destination) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija ne postoji: " + id));

        String trimmed = (destination == null) ? null : destination.strip();
        booking.setAssignedDestination(trimmed);

        // Generiši token tek kad je destinacija unesena i još nema tokena
        if (trimmed != null && !trimmed.isEmpty() && booking.getRevealToken() == null) {
            booking.setRevealToken(UUID.randomUUID().toString().replace("-", ""));
        }

        // Ako admin briše destinaciju, resetuj i token i sentAt
        if (trimmed == null || trimmed.isEmpty()) {
            booking.setRevealToken(null);
            booking.setRevealSentAt(null);
        }

        Booking saved = bookingRepository.save(booking);
        log.info("[ADMIN] Destinacija za rezervaciju {} → '{}'", saved.getBookingRef(), trimmed);
        return adminBookingMapper.toResponse(saved);
    }

    // ══ HELPERS ══════════════════════════════════════════════════════════════

    private AvailableDate findDateOrThrow(Long id) {
        return availableDateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Termin ne postoji: " + id));
    }
}
