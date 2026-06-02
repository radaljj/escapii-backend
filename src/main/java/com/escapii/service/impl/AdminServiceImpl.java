package com.escapii.service.impl;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.AdminDateRequest;
import com.escapii.dto.AdminDateResponse;
import com.escapii.dto.CreatePrivateDateRequest;
import com.escapii.dto.CustomDateInquiryResponse;
import com.escapii.dto.DestinationResponse;
import com.escapii.mapper.AdminBookingMapper;
import com.escapii.mapper.DestinationMapper;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.CustomDateInquiry;
import com.escapii.model.Destination;
import com.escapii.model.GiftVoucher;
import com.escapii.model.InquiryStatus;
import com.escapii.model.RevealEvent;
import com.escapii.model.VoucherStatus;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.CustomDateInquiryRepository;
import com.escapii.repository.DestinationRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.repository.RevealEventRepository;
import com.escapii.service.AdminService;
import com.escapii.service.AvailableDateService;
import com.escapii.service.CustomDateInquiryService;
import com.escapii.service.email.BookingEmailService;
import com.escapii.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.escapii.util.TokenUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AvailableDateRepository    availableDateRepository;
    private final DestinationRepository      destinationRepository;
    private final BookingRepository          bookingRepository;
    private final GiftVoucherRepository      giftVoucherRepository;
    private final RevealEventRepository      revealEventRepository;
    private final CustomDateInquiryRepository inquiryRepository;
    private final AdminBookingMapper         adminBookingMapper;
    private final DestinationMapper          destinationMapper;
    private final BookingEmailService        bookingEmailService;
    private final WaitlistService            waitlistService;
    private final AvailableDateService       availableDateService;
    private final CustomDateInquiryService   inquiryService;

    // ══ DESTINACIJE ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<DestinationResponse> getAllDestinations() {
        return destinationMapper.toResponseList(destinationRepository.findAllByOrderByNameAsc());
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "destinations", allEntries = true),
        @CacheEvict(value = "active-destinations", allEntries = true)
    })
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
    @CacheEvict(value = "active-dates", allEntries = true)
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
        date.getPotentialDestinations().clear();
        date.getPotentialDestinations().addAll(destinations);
        AvailableDate saved = availableDateRepository.save(date);
        log.info("[ADMIN] Azurirane destinacije za termin id={} → {}",
                id, destinations.stream().map(Destination::getName).toList());
        return new AdminDateResponse(saved);
    }

    @Override
    @CacheEvict(value = "active-dates", allEntries = true)
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
    @CacheEvict(value = "active-dates", allEntries = true)
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
    @CacheEvict(value = "active-dates", allEntries = true)
    @Transactional
    public void deleteDate(Long id) {
        AvailableDate date = findDateOrThrow(id);

        long bookingCount = bookingRepository.countBySelectedDateId(id);
        if (bookingCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Termin ne može biti obrisan jer postoji " + bookingCount +
                    " rezervaci" + (bookingCount == 1 ? "ja" : (bookingCount < 5 ? "je" : "ja")) +
                    " u istoriji (uključujući otkazane). Deaktivirajte termin umesto brisanja.");
        }

        availableDateRepository.deleteById(id);
        log.info("[ADMIN] Obrisan termin id={} | {} → {} | aerodrom={}",
                id, date.getDepartureDate(), date.getReturnDate(), date.getDepartureAirport());
    }

    // ══ REZERVACIJE ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<AdminBookingResponse> getAllBookings() {
        List<AdminBookingResponse> responses = adminBookingMapper.toResponseList(
                bookingRepository.findAllByOrderByCreatedAtDesc());

        // Batch fetch reveal events — jedan upit za sve rezervacije
        List<String> refs = responses.stream().map(AdminBookingResponse::getBookingRef).toList();
        Map<String, java.time.LocalDateTime> revealedMap = revealEventRepository
                .findAllByBookingRefIn(refs).stream()
                .collect(Collectors.toMap(RevealEvent::getBookingRef, RevealEvent::getRevealedAt));

        responses.forEach(r -> r.setDestinationRevealedAt(revealedMap.get(r.getBookingRef())));
        return responses;
    }

    @Override
    @Transactional
    public void deleteBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija ne postoji: " + id));

        BookingStatus status    = booking.getStatus();
        BookingStatus oldStatus = booking.getOldStatus();

        boolean wasPaidOrConfirmed = status    == BookingStatus.CONFIRMED
                                  || oldStatus == BookingStatus.CONFIRMED;
        if (wasPaidOrConfirmed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Rezervacija " + booking.getBookingRef() +
                    " ne može biti obrisana jer je bila potvrđena. " +
                    "Otkaži je ako je to potrebno.");
        }

        // Oslobodi vaučer ako je booking bio vezan za njega.
        // Vaučer se oslobađa samo ako je RESERVED (booking bio aktivan, nije završen) —
        // USED vaučer se NE oslobađa jer to znači da je putovanje završeno pre brisanja.
        // Provera i po appliedVoucherCode i po usedInBookingRef da pokrijemo sve edge caseove.
        String voucherCode = booking.getAppliedVoucherCode();
        if (voucherCode != null) {
            giftVoucherRepository.findByCode(voucherCode).ifPresent(v -> {
                if (v.getStatus() == VoucherStatus.RESERVED) {
                    v.setStatus(VoucherStatus.ACTIVE);
                    v.setUsedAt(null);
                    v.setUsedInBookingRef(null);
                    giftVoucherRepository.save(v);
                    log.info("[Voucher] {} → ACTIVE (booking {} obrisan, bio RESERVED)", v.getCode(), booking.getBookingRef());
                }
                // USED vaučer ostaje USED — putovanje je završeno, vaučer je trajno iskorišćen
            });
        }

        bookingRepository.deleteById(id);
        log.info("[ADMIN] Obrisana rezervacija id={} ref={} | status={} oldStatus={}",
                id, booking.getBookingRef(), status, oldStatus);
    }

    @Override
    @Transactional
    public AdminBookingResponse updateBookingStatus(Long id, BookingStatus status) {
        // findWithDetailsById — učitava sve LAZY asocijacije (excluded destinations, passengers)
        // da bi @Async email servis mogao da pristupi njima van transakcije
        Booking booking = bookingRepository.findWithDetailsById(id)
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

        // ── Vaučer lifecycle ──────────────────────────────────────────────────
        // CONFIRMED / PENDING  : vaučer ostaje RESERVED (putovanje još nije završeno)
        // → COMPLETED          : vaučer trajno USED (putovanje završeno)
        // → CANCELLED          : vaučer oslobođen → ACTIVE (bez obzira na prethodni status)
        if (saved.getAppliedVoucherCode() != null) {
            giftVoucherRepository.findByCode(saved.getAppliedVoucherCode()).ifPresent(v -> {
                if (status == BookingStatus.COMPLETED && v.getStatus() == VoucherStatus.RESERVED) {
                    v.setStatus(VoucherStatus.USED);
                    v.setUsedAt(LocalDateTime.now());
                    giftVoucherRepository.save(v);
                    log.info("[Voucher] {} → USED (booking {} COMPLETED)", v.getCode(), saved.getBookingRef());
                } else if (status == BookingStatus.CANCELLED
                        && (v.getStatus() == VoucherStatus.RESERVED || v.getStatus() == VoucherStatus.USED)) {
                    v.setStatus(VoucherStatus.ACTIVE);
                    v.setUsedAt(null);
                    v.setUsedInBookingRef(null);
                    giftVoucherRepository.save(v);
                    log.info("[Voucher] {} → ACTIVE (booking {} CANCELLED)", v.getCode(), saved.getBookingRef());
                }
                // PENDING / CONFIRMED — vaučer ostaje RESERVED, nema promene
            });
        }

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
            booking.setRevealToken(TokenUtils.generate());
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

    @Override
    @Transactional
    public AdminBookingResponse setAirlineName(Long id, String name) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija ne postoji: " + id));
        String trimmed = (name == null || name.isBlank()) ? null : name.strip();
        if (trimmed != null && trimmed.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Naziv avio kompanije ne sme biti duži od 100 karaktera");
        }
        booking.setAirlineName(trimmed);
        Booking saved = bookingRepository.save(booking);
        log.info("[ADMIN] Airline name za {} → '{}'", saved.getBookingRef(), trimmed);
        return adminBookingMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AdminBookingResponse setAirlineBookingCode(Long id, String code) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija ne postoji: " + id));
        String trimmed = (code == null || code.isBlank()) ? null : code.strip().toUpperCase();
        if (trimmed != null && trimmed.length() > 25) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Booking kod ne sme biti duži od 20 karaktera");
        }
        booking.setAirlineBookingCode(trimmed);
        Booking saved = bookingRepository.save(booking);
        log.info("[ADMIN] Airline booking code za {} → '{}'", saved.getBookingRef(), trimmed);
        return adminBookingMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AdminBookingResponse markRevealBoxSent(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija ne postoji: " + id));
        if (!Boolean.TRUE.equals(booking.getHasRevealBox())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ova rezervacija nema Reveal Box.");
        }
        booking.setRevealBoxSent(true);
        Booking saved = bookingRepository.save(booking);
        log.info("[ADMIN] Reveal Box označen kao poslan za {}", saved.getBookingRef());
        return adminBookingMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AdminBookingResponse setWeatherCity(Long id, String weatherCity) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija ne postoji: " + id));
        String trimmed = (weatherCity == null || weatherCity.isBlank()) ? null : weatherCity.strip();
        booking.setWeatherCity(trimmed);
        Booking saved = bookingRepository.save(booking);
        log.info("[ADMIN] Weather city za {} → '{}'", saved.getBookingRef(), trimmed);
        return adminBookingMapper.toResponse(saved);
    }

    // ══ PRIVATNI TERMINI ═════════════════════════════════════════════════════

    @Override
    @Transactional
    public AdminDateResponse makePrivate(Long dateId, int travelers, int expiresInHours, Integer pricePerPerson) {
        AvailableDate saved = availableDateService.makePrivate(dateId, travelers, expiresInHours, pricePerPerson);
        return new AdminDateResponse(saved);
    }

    // ══ UPITI ZA CUSTOM TERMINE ══════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<CustomDateInquiryResponse> getAllInquiries() {
        return inquiryService.getAllInquiries();
    }

    @Override
    @Transactional
    public CustomDateInquiryResponse updateInquiryStatus(Long id, InquiryStatus status) {
        return inquiryService.updateStatus(id, status);
    }

    @Override
    @Transactional
    public CustomDateInquiryResponse updateInquiryPrice(Long id, BigDecimal price) {
        return inquiryService.updatePrice(id, price);
    }

    @Override
    @Transactional
    public void deleteInquiry(Long id) {
        if (!inquiryRepository.existsById(id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Upit nije pronađen: " + id);
        }
        inquiryRepository.deleteById(id);
        log.info("[ADMIN] Obrisan upit id={}", id);
    }

    // ══ KREIRANJE PRIVATNOG TERMINA IZ UPITA ════════════════════════════════

    @Override
    @Transactional
    public AdminDateResponse createPrivateDateFromInquiry(Long inquiryId, CreatePrivateDateRequest req) {
        CustomDateInquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Upit nije pronađen: " + inquiryId));

        LocalDate depDate = inquiry.getDesiredDepartureDate();
        LocalDate retDate = depDate.plusDays(inquiry.getNights());

        AvailableDate date = new AvailableDate();
        date.setDepartureDate(depDate);
        date.setReturnDate(retDate);
        date.setNumberOfNights(inquiry.getNights());
        date.setDepartureAirport(inquiry.getAirport());
        date.setAvailableSlots(req.travelers());
        date.setBasePrice(req.pricePerPerson());
        date.setActive(true);
        // Odmah privatan — nikad nije javno vidljiv
        date.setIsPrivate(true);
        date.setPrivateToken(TokenUtils.generate());
        date.setExpiresAt(LocalDateTime.now().plusHours(req.effectiveExpiry()));

        AvailableDate saved = availableDateRepository.save(date);
        log.info("[ADMIN] Privatni termin kreiran za upit id={} | {} → {} | token={} | {}€/os | expiresAt={}",
                inquiryId, depDate, retDate, saved.getPrivateToken(), req.pricePerPerson(), saved.getExpiresAt());

        return new AdminDateResponse(saved);
    }

    // ══ HELPERS ══════════════════════════════════════════════════════════════

    private AvailableDate findDateOrThrow(Long id) {
        return availableDateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Termin ne postoji: " + id));
    }
}
