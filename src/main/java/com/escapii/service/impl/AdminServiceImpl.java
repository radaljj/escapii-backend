package com.escapii.service.impl;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.AdminDateRequest;
import com.escapii.dto.AdminDateResponse;
import com.escapii.dto.CreatePrivateDateRequest;
import com.escapii.dto.CustomDateInquiryResponse;
import com.escapii.dto.DestinationRequest;
import com.escapii.dto.DestinationResponse;
import com.escapii.mapper.AdminBookingMapper;
import com.escapii.mapper.DestinationMapper;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.CustomDateInquiry;
import com.escapii.model.Destination;
import com.escapii.dto.TermDestinationResponse;
import com.escapii.model.InquiryStatus;
import com.escapii.model.RevealEvent;
import com.escapii.model.TermDestination;
import com.escapii.model.VoucherStatus;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.CustomDateInquiryRepository;
import com.escapii.repository.DestinationRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.repository.RevealEventRepository;
import com.escapii.repository.TermDestinationRepository;
import com.escapii.service.AdminService;
import com.escapii.service.AirportLookupService;
import com.escapii.service.AvailableDateService;
import com.escapii.service.CustomDateInquiryService;
import com.escapii.service.InvoiceService;
import com.escapii.service.email.BookingEmailService;
import com.escapii.service.email.ConfirmationDocumentEmailService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import net.coobird.thumbnailator.Thumbnails;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    private final AvailableDateRepository     availableDateRepository;
    private final DestinationRepository       destinationRepository;
    private final TermDestinationRepository   termDestinationRepository;
    private final BookingRepository           bookingRepository;
    private final GiftVoucherRepository       giftVoucherRepository;
    private final RevealEventRepository       revealEventRepository;
    private final CustomDateInquiryRepository inquiryRepository;
    private final AdminBookingMapper          adminBookingMapper;
    private final DestinationMapper           destinationMapper;
    private final BookingEmailService         bookingEmailService;
    private final WaitlistService             waitlistService;
    private final AvailableDateService        availableDateService;
    private final CustomDateInquiryService    inquiryService;
    private final AirportLookupService        airportLookupService;
    private final InvoiceService              invoiceService;
    private final ConfirmationDocumentEmailService confirmationDocumentEmailService;

    // ══ DESTINACIJE ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<DestinationResponse> getAllDestinations() {
        return destinationMapper.toResponseList(destinationRepository.findAllByOrderByNameAsc());
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "destinations", allEntries = true),
        @CacheEvict(value = "destinations-by-airport", allEntries = true)
    })
    @Transactional
    public DestinationResponse createDestination(DestinationRequest request) {
        if (destinationRepository.existsByName(request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Destinacija sa imenom '" + request.name() + "' već postoji");
        }
        String iata = request.airportCode().toUpperCase();
        Destination d = new Destination();
        d.setName(request.name());
        d.setAirportCode(iata);
        d.setCountry(request.country());
        d.setRegion(request.region());
        d.setDepartureAirports(request.departureAirports().stream()
                .map(String::toUpperCase).collect(Collectors.toSet()));
        d.setActive(true);
        d.setNameEn(airportLookupService.cityEn(iata).orElse(null));
        d.setCountryEn(airportLookupService.countryEn(iata).orElse(null));
        Destination saved = destinationRepository.save(d);
        log.info("[ADMIN] Nova destinacija kreirana: '{}' (id={}) EN: {}/{}", saved.getName(), saved.getId(), saved.getNameEn(), saved.getCountryEn());
        return destinationMapper.toResponse(saved);
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "destinations", allEntries = true),
        @CacheEvict(value = "destinations-by-airport", allEntries = true)
    })
    @Transactional
    public DestinationResponse updateDestination(Long id, DestinationRequest request) {
        Destination d = destinationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Destinacija ne postoji: " + id));
        String iata = request.airportCode().toUpperCase();
        d.setName(request.name());
        d.setAirportCode(iata);
        d.setCountry(request.country());
        d.setRegion(request.region());
        d.setDepartureAirports(request.departureAirports().stream()
                .map(String::toUpperCase).collect(Collectors.toSet()));
        d.setNameEn(airportLookupService.cityEn(iata).orElse(null));
        d.setCountryEn(airportLookupService.countryEn(iata).orElse(null));
        log.info("[ADMIN] Destinacija '{}' (id={}) ažurirana EN: {}/{}", d.getName(), id, d.getNameEn(), d.getCountryEn());
        return destinationMapper.toResponse(destinationRepository.save(d));
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "destinations", allEntries = true),
        @CacheEvict(value = "destinations-by-airport", allEntries = true)
    })
    @Transactional
    public void deleteDestination(Long id) {
        Destination d = destinationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Destinacija ne postoji: " + id));
        // Ukloni FK reference iz term_destination tabele
        termDestinationRepository.deleteByDestinationId(id);
        deleteImageFile(d.getImageUrl());
        destinationRepository.delete(d);
        log.info("[ADMIN] Destinacija '{}' (id={}) obrisana", d.getName(), id);
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "destinations", allEntries = true),
        @CacheEvict(value = "destinations-by-airport", allEntries = true)
    })
    @Transactional
    public DestinationResponse uploadDestinationImage(Long id, MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !Set.of("image/jpeg", "image/png", "image/webp").contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Dozvoljeni formati: JPG, PNG, WebP");
        }
        Destination d = destinationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Destinacija ne postoji: " + id));
        deleteImageFile(d.getImageUrl());
        try {
            String filename = UUID.randomUUID() + ".jpg";
            Path destDir    = Paths.get(uploadsDir, "destinations");
            Files.createDirectories(destDir);
            Thumbnails.of(file.getInputStream())
                    .size(1200, 1200)
                    .keepAspectRatio(true)
                    .outputFormat("jpg")
                    .outputQuality(0.85f)
                    .toFile(destDir.resolve(filename).toFile());
            d.setImageUrl(backendUrl + "/uploads/destinations/" + filename);
            log.info("[ADMIN] Slika uploadovana za destinaciju id={}: {}", id, filename);
            return destinationMapper.toResponse(destinationRepository.save(d));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Greška pri čuvanju slike: " + e.getMessage());
        }
    }

    private void deleteImageFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        try {
            String filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
            Path path = Paths.get(uploadsDir, "destinations", filename);
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("[ADMIN] Nije moguće obrisati sliku: {}", e.getMessage());
        }
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

        // Izračunaj numberOfNights automatski - ne primamo od klijenta da ne bi bilo nekonzistentnosti
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

        // Inicijalne destinacije (ako su prosleđene pri kreiranju termina)
        AvailableDate saved = availableDateRepository.save(date);
        if (req.getDestinationIds() != null && !req.getDestinationIds().isEmpty()) {
            List<Destination> destinations = destinationRepository.findAllById(req.getDestinationIds());
            destinations.forEach(dest -> saved.getTermDestinations().add(new TermDestination(saved, dest)));
            availableDateRepository.save(saved);
        }

        log.info("[ADMIN] Dodat termin id={} | {} → {} | aerodrom={}",
                saved.getId(), saved.getDepartureDate(), saved.getReturnDate(), saved.getDepartureAirport());

        return new AdminDateResponse(saved);
    }

    // ══ PER-TERMIN DESTINACIJE ════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<TermDestinationResponse> getTermDestinations(Long dateId) {
        findDateOrThrow(dateId);
        return termDestinationRepository.findByDateIdOrderByDestinationNameAsc(dateId)
                .stream().map(TermDestinationResponse::new).toList();
    }

    @Override
    @Transactional
    public TermDestinationResponse addDestinationToTerm(Long dateId, Long destinationId) {
        AvailableDate date = findDateOrThrow(dateId);
        if (termDestinationRepository.existsByDateIdAndDestinationId(dateId, destinationId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Destinacija već postoji u ovom terminu");
        }
        Destination dest = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Destinacija ne postoji: " + destinationId));
        TermDestination td = termDestinationRepository.save(new TermDestination(date, dest));
        log.info("[ADMIN] Destinacija '{}' dodana u termin id={}", dest.getName(), dateId);
        return new TermDestinationResponse(td);
    }

    @Override
    @Transactional
    public void removeDestinationFromTerm(Long dateId, Long destinationId) {
        TermDestination td = termDestinationRepository.findByDateIdAndDestinationId(dateId, destinationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Destinacija nije u ovom terminu"));
        long totalInTerm = termDestinationRepository.findByDateIdOrderByDestinationNameAsc(dateId).size();
        if (totalInTerm <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Termin mora imati bar jednu destinaciju");
        }
        termDestinationRepository.delete(td);
        log.info("[ADMIN] Destinacija id={} uklonjena iz termina id={}", destinationId, dateId);
    }

    @Override
    @Transactional
    public TermDestinationResponse toggleTermDestination(Long dateId, Long destinationId, boolean active) {
        TermDestination td = termDestinationRepository.findByDateIdAndDestinationId(dateId, destinationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Destinacija nije u ovom terminu"));
        if (!active) {
            long activeCount = termDestinationRepository.findActiveByDateId(dateId).size();
            if (activeCount <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Termin mora imati bar jednu aktivnu destinaciju");
            }
        }
        td.setActive(active);
        TermDestination saved = termDestinationRepository.save(td);
        log.info("[ADMIN] Destinacija '{}' {} za termin id={}",
                saved.getDestination().getName(), active ? "aktivirana" : "deaktivirana", dateId);
        return new TermDestinationResponse(saved);
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
    public void updatePrice(Long id, int price) {
        if (price < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cena mora biti pozitivna");
        }
        AvailableDate date = findDateOrThrow(id);
        int oldPrice = date.getBasePrice();
        date.setBasePrice(price);
        availableDateRepository.save(date);
        log.info("[ADMIN] Termin id={} | cena: {}€ → {}€", id, oldPrice, price);
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

        // Batch fetch reveal events - jedan upit za sve rezervacije
        List<String> refs = responses.stream().map(AdminBookingResponse::getBookingRef).toList();
        Map<String, java.time.LocalDateTime> revealedMap = revealEventRepository
                .findAllByBookingRefIn(refs).stream()
                .collect(Collectors.toMap(RevealEvent::getBookingRef, RevealEvent::getRevealedAt));

        responses.forEach(r -> r.setDestinationRevealedAt(revealedMap.get(r.getBookingRef())));

        // Popuni termDestinations per booking (destinacije dostupne za taj termin)
        responses.forEach(r -> {
            if (r.getSelectedDateId() != null) {
                List<com.escapii.dto.TermDestinationResponse> termDests =
                        termDestinationRepository.findByDateIdOrderByDestinationNameAsc(r.getSelectedDateId())
                                .stream()
                                .map(com.escapii.dto.TermDestinationResponse::new)
                                .toList();
                r.setTermDestinations(termDests);
            }
        });

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
        // Reversujemo usedAmount za iznos koji je bio primenjen u ovom bookingu.
        // RESERVED → ACTIVE (u potpunosti oslobađamo reserve).
        // ACTIVE sa usedAmount > 0 → smanjujemo usedAmount (delimično oslobađamo).
        // USED vaučer se NE menja - putovanje je završeno pre brisanja.
        String voucherCode = booking.getAppliedVoucherCode();
        if (voucherCode != null) {
            giftVoucherRepository.findByCode(voucherCode).ifPresent(v -> {
                if (v.getStatus() == VoucherStatus.RESERVED || v.getStatus() == VoucherStatus.ACTIVE) {
                    Integer disc = booking.getVoucherDiscount();
                    if (disc != null && disc > 0) {
                        java.math.BigDecimal reversed = v.getUsedAmount()
                                .subtract(java.math.BigDecimal.valueOf(disc));
                        v.setUsedAmount(reversed.compareTo(java.math.BigDecimal.ZERO) < 0
                                ? java.math.BigDecimal.ZERO : reversed);
                    }
                    if (v.getStatus() == VoucherStatus.RESERVED) {
                        v.setStatus(VoucherStatus.ACTIVE);
                        v.setUsedAt(null);
                    }
                    giftVoucherRepository.save(v);
                    log.info("[Voucher] {} → usedAmount reversovano za {}€ (booking {} obrisan), novo usedAmount={}€",
                            v.getCode(), booking.getVoucherDiscount(), booking.getBookingRef(), v.getUsedAmount());
                }
                // USED vaučer ostaje USED - putovanje je završeno, vaučer je trajno iskorišćen
            });
        }

        bookingRepository.deleteById(id);
        log.info("[ADMIN] Obrisana rezervacija id={} ref={} | status={} oldStatus={}",
                id, booking.getBookingRef(), status, oldStatus);
    }

    @Override
    @Transactional
    @CacheEvict(value = "active-dates", allEntries = true)
    public AdminBookingResponse updateBookingStatus(Long id, BookingStatus status) {
        // findWithDetailsById - učitava sve LAZY asocijacije (excluded destinations, passengers)
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
        // usedAmount se upisuje odmah pri kreiranju rezervacije.
        // COMPLETED : ako je vaučer u potpunosti potrošen (RESERVED) → postaje USED.
        //             ako je delimično potrošen (ACTIVE) → ostaje ACTIVE, nema promene.
        // CANCELLED : reversujemo usedAmount za ovaj booking; vaučer → ACTIVE.
        // PENDING / CONFIRMED - nema promene.
        if (saved.getAppliedVoucherCode() != null) {
            giftVoucherRepository.findByCode(saved.getAppliedVoucherCode()).ifPresent(v -> {
                if (status == BookingStatus.COMPLETED) {
                    if (v.getStatus() == VoucherStatus.RESERVED) {
                        // Vaučer je bio u potpunosti potrošen - finalizuj kao USED
                        v.setStatus(VoucherStatus.USED);
                        v.setUsedAt(LocalDateTime.now());
                        giftVoucherRepository.save(v);
                        log.info("[Voucher] {} → USED (booking {} COMPLETED, {}€ od {}€ potrošeno)",
                                v.getCode(), saved.getBookingRef(), v.getUsedAmount(), v.getAmount());
                    }
                    // Delimično potrošen (ACTIVE) - ostaje ACTIVE, usedAmount je već tačan
                } else if (status == BookingStatus.CANCELLED) {
                    // Reversiraj usedAmount za ovaj booking
                    Integer disc = saved.getVoucherDiscount();
                    if (disc != null && disc > 0) {
                        java.math.BigDecimal reversed = v.getUsedAmount()
                                .subtract(java.math.BigDecimal.valueOf(disc));
                        v.setUsedAmount(reversed.compareTo(java.math.BigDecimal.ZERO) < 0
                                ? java.math.BigDecimal.ZERO : reversed);
                    }
                    v.setStatus(VoucherStatus.ACTIVE);
                    v.setUsedAt(null);
                    giftVoucherRepository.save(v);
                    log.info("[Voucher] {} → ACTIVE (booking {} CANCELLED, reversovano {}€, novo usedAmount={}€)",
                            v.getCode(), saved.getBookingRef(), saved.getVoucherDiscount(), v.getUsedAmount());
                }
                // PENDING / CONFIRMED - nema promene
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
        if (trimmed != null && trimmed.length() > 20) {
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
        // Odmah privatan - nikad nije javno vidljiv
        date.setIsPrivate(true);
        date.setPrivateToken(TokenUtils.generate());
        date.setExpiresAt(LocalDateTime.now().plusHours(req.effectiveExpiry()));
        // Zapamti kome ide privatni link - upit se kasnije može obrisati.
        date.setClientEmail(inquiry.getEmail());

        AvailableDate saved = availableDateRepository.save(date);
        log.info("[ADMIN] Privatni termin kreiran za upit id={} | {} → {} | token={} | {}€/os | expiresAt={}",
                inquiryId, depDate, retDate, saved.getPrivateToken(), req.pricePerPerson(), saved.getExpiresAt());

        return new AdminDateResponse(saved);
    }

    // ══ FAKTURE ══════════════════════════════════════════════════════════════
    // Implementacija u InvoiceService/InvoiceServiceImpl - ovde samo delegacija,
    // isti pattern kao ostali specijalizovani servisi (bookingEmailService, itd.)

    @Override
    public AdminBookingResponse sendInvoice(Long bookingId) {
        return invoiceService.sendInvoice(bookingId);
    }

    @Override
    public com.escapii.dto.GiftVoucherResponse sendVoucherInvoice(Long voucherId) {
        return invoiceService.sendVoucherInvoice(voucherId);
    }

    // ══ DOKUMENT REZERVACIJE (od partnerske agencije) ═══════════════════════════

    private static final long MAX_CONFIRMATION_DOCUMENT_SIZE = 10L * 1024 * 1024; // 10 MB

    @Override
    @Transactional
    public AdminBookingResponse uploadConfirmationDocument(Long bookingId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fajl je prazan.");
        }
        String contentType = file.getContentType();
        if (!"application/pdf".equals(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Dozvoljen je samo PDF fajl.");
        }
        if (file.getSize() > MAX_CONFIRMATION_DOCUMENT_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Fajl je prevelik (maks 10 MB).");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija nije pronađena: " + bookingId));

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Greška pri čitanju fajla.");
        }

        booking.setConfirmationDocument(bytes);
        booking.setConfirmationDocumentFilename(StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "rezervacija.pdf");
        booking.setConfirmationDocumentUploadedAt(LocalDateTime.now());
        // Novi upload poništava prethodno "poslato" stanje - odluka o ponovnom
        // slanju je eksplicitna admin akcija (resendConfirmationDocument),
        // sprečava neočekivano duplo automatsko slanje pri zameni fajla.
        booking.setConfirmationSentAt(null);
        Booking saved = bookingRepository.save(booking);

        // Ako je korisnik VEĆ potvrdio da je video reveal (RevealEvent postoji),
        // šaljemo odmah - nema razloga da čekamo, dokument je upravo stigao.
        boolean alreadyViewed = Boolean.TRUE.equals(booking.getHasRevealBox()) == false
                && revealEventRepository.findByBookingRef(booking.getBookingRef()).isPresent();
        if (alreadyViewed) {
            // Vreme se upisuje SAMO ako je mejl stvarno otišao - inače bi panel
            // pokazivao "poslato" za mejl koji kupac nikad nije dobio.
            if (confirmationDocumentEmailService.sendConfirmationDocument(saved)) {
                saved.setConfirmationSentAt(LocalDateTime.now());
                saved = bookingRepository.save(saved);
                log.info("[ConfirmationDocument] Uploadovan i odmah poslat za {} (reveal već viđen)", saved.getBookingRef());
            } else {
                log.error("[ConfirmationDocument] Slanje nije uspelo za {} - ostaje neposlat, može ponovo",
                        saved.getBookingRef());
            }
        } else {
            log.info("[ConfirmationDocument] Uploadovan za {} - čeka se da korisnik pogleda reveal", saved.getBookingRef());
        }

        return adminBookingMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AdminBookingResponse resendConfirmationDocument(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija nije pronađena: " + bookingId));

        if (booking.getConfirmationDocument() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Dokument nije uploadovan za ovu rezervaciju.");
        }

        // Dokument nosi destinaciju - i u telu i u naslovu mejla. Ne sme stići
        // pre nego što je kupac sazna, inače pokvari otkrivanje.
        //
        // Reveal mejl NE sadrži destinaciju, samo link - kupac je sazna tek kad
        // otvori stranicu. Zato se čeka RevealEvent (otvoreno), a ne revealSentAt
        // (poslato); isti uslov koji koristi i automatski put posle upload-a.
        //
        // Kod Reveal Box-a nema šta da se čeka: sistem ne može znati kad je kupac
        // otvorio fizičku kutiju, a scheduler te rezervacije preskače pa se
        // revealSentAt nikad i ne postavi. Tu odluku donosi admin.
        if (!Boolean.TRUE.equals(booking.getHasRevealBox())
                && revealEventRepository.findByBookingRef(booking.getBookingRef()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Kupac još nije otvorio reveal. Dokument sadrži destinaciju i ne sme stići pre toga.");
        }

        if (!confirmationDocumentEmailService.sendConfirmationDocument(booking)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Slanje nije uspelo. Rezervacija ostaje označena kao neposlata - pokušaj ponovo.");
        }
        booking.setConfirmationSentAt(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);

        log.info("[ConfirmationDocument] Ručno ponovo poslat za {}", saved.getBookingRef());
        return adminBookingMapper.toResponse(saved);
    }

    // ══ HELPERS ══════════════════════════════════════════════════════════════

    private AvailableDate findDateOrThrow(Long id) {
        return availableDateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Termin ne postoji: " + id));
    }
}
