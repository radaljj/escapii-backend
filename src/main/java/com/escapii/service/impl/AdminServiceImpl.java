package com.escapii.service.impl;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.AdminDateRequest;
import com.escapii.dto.AdminDateResponse;
import com.escapii.dto.AgencyCostsRequest;
import com.escapii.dto.AgencyEarningsResponse;
import com.escapii.dto.AgencySettlementResponse;
import com.escapii.model.BookingFinancialItem;
import com.escapii.model.ItemType;
import com.escapii.model.SettlementStatus;
import com.escapii.repository.BookingFinancialItemRepository;
import com.escapii.service.AgencySettlementCalculator;
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
import com.escapii.dto.AgencyRequest;
import com.escapii.dto.AgencyResponse;
import com.escapii.model.Agency;
import com.escapii.repository.AgencyRepository;
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
import com.escapii.event.BookingEmailEvent;
import com.escapii.service.email.ConfirmationDocumentEmailService;
import com.escapii.util.LogUtils;
import com.escapii.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
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

    private final AgencyRepository            agencyRepository;
    private final AvailableDateRepository     availableDateRepository;
    private final DestinationRepository       destinationRepository;
    private final TermDestinationRepository   termDestinationRepository;
    private final BookingRepository           bookingRepository;
    private final GiftVoucherRepository       giftVoucherRepository;
    private final RevealEventRepository       revealEventRepository;
    private final CustomDateInquiryRepository inquiryRepository;
    private final AdminBookingMapper          adminBookingMapper;
    private final DestinationMapper           destinationMapper;
    private final ApplicationEventPublisher   eventPublisher;
    private final WaitlistService             waitlistService;
    private final AvailableDateService        availableDateService;
    private final CustomDateInquiryService    inquiryService;
    private final AirportLookupService        airportLookupService;
    private final InvoiceService              invoiceService;
    private final ConfirmationDocumentEmailService confirmationDocumentEmailService;
    private final ConfirmationDocumentAutoSender confirmationDocumentAutoSender;
    private final AgencySettlementCalculator agencySettlementCalculator;
    private final BookingFinancialItemRepository bookingFinancialItemRepository;
    private final com.escapii.repository.AgencyInvoiceSequenceRepository agencyInvoiceSequenceRepository;

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

        if (req.getAgencyId() != null) {
            Agency agency = agencyRepository.findById(req.getAgencyId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Agencija nije pronađena: " + req.getAgencyId()));
            date.setAgency(agency);
        }

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
    @Transactional
    public TermDestinationResponse toggleConnecting(Long dateId, Long destinationId, boolean connecting) {
        TermDestination td = termDestinationRepository.findByDateIdAndDestinationId(dateId, destinationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Destinacija nije u ovom terminu"));
        td.setConnecting(connecting);
        TermDestination saved = termDestinationRepository.save(td);
        log.info("[ADMIN] Destinacija '{}' označena kao {} za termin id={}",
                saved.getDestination().getName(), connecting ? "presedanje" : "direktan let", dateId);
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

        // Popuni termDestinations per booking - batch upit (sprečava N+1)
        Set<Long> dateIds = responses.stream()
                .map(AdminBookingResponse::getSelectedDateId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        if (!dateIds.isEmpty()) {
            Map<Long, List<com.escapii.dto.TermDestinationResponse>> byDateId =
                    termDestinationRepository.findByDateIdIn(dateIds).stream()
                            .collect(Collectors.groupingBy(
                                    td -> td.getDate().getId(),
                                    Collectors.mapping(com.escapii.dto.TermDestinationResponse::new, Collectors.toList())));

            responses.forEach(r -> {
                if (r.getSelectedDateId() != null) {
                    r.setTermDestinations(byDateId.getOrDefault(r.getSelectedDateId(), List.of()));
                }
            });
        }

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
            // findByCodeForUpdate (ne findByCode) - zaključava red da ne bi istovremeni
            // booking sa istim kodom video zastarelo stanje (izgubljena izmena/race).
            giftVoucherRepository.findByCodeForUpdate(voucherCode).ifPresent(v -> {
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
                            LogUtils.maskVoucherCode(v.getCode()), booking.getVoucherDiscount(), booking.getBookingRef(), v.getUsedAmount());
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

        // Guard: booking ne sme u CONFIRMED bez agencije - inače bi bio "nevidljiv"
        // u earnings dashboardu (grupiše se po agencyIdSnapshot). Privatni termin sme
        // ostati bez agencije dok se ne pronađe organizator, ali potvrda bookinga
        // zahteva da je agencija već dodeljena terminu (snapshot se pravi malo niže).
        // Provera samo pri PRELAZU u CONFIRMED - CONFIRMED→CONFIRMED je no-op, a snapshot
        // koji već postoji znači da je agencija u prošlosti bila vezana pa je sigurno.
        if (status == BookingStatus.CONFIRMED && oldStatus != BookingStatus.CONFIRMED
                && booking.getAgencyIdSnapshot() == null) {
            AvailableDate confirmDate = availableDateRepository.findByBookingId(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Termin ne postoji za booking: " + id));
            if (confirmDate.getAgency() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Termin nema dodeljenu agenciju. Dodeli agenciju terminu pre potvrde rezervacije.");
            }
        }

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
        // CANCELLED → PENDING/CONFIRMED (un-cancel) : ponovo zaključavamo vaučer -
        //             bez ovoga ostaje ACTIVE (slobodan za drugu rezervaciju) dok
        //             ova rezervacija i dalje nosi popust u ceni.
        // Ostali prelazi - nema promene.
        if (saved.getAppliedVoucherCode() != null) {
            // findByCodeForUpdate (ne findByCode) - zaključava red da ne bi istovremeni
            // booking sa istim kodom video zastarelo stanje (izgubljena izmena/race).
            giftVoucherRepository.findByCodeForUpdate(saved.getAppliedVoucherCode()).ifPresent(v -> {
                if (status == BookingStatus.COMPLETED) {
                    if (v.getStatus() == VoucherStatus.RESERVED) {
                        // Vaučer je bio u potpunosti potrošen - finalizuj kao USED
                        v.setStatus(VoucherStatus.USED);
                        v.setUsedAt(LocalDateTime.now());
                        giftVoucherRepository.save(v);
                        log.info("[Voucher] {} → USED (booking {} COMPLETED, {}€ od {}€ potrošeno)",
                                LogUtils.maskVoucherCode(v.getCode()), saved.getBookingRef(), v.getUsedAmount(), v.getAmount());
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
                    v.setUsedInBookingRef(null);
                    giftVoucherRepository.save(v);
                    log.info("[Voucher] {} → ACTIVE (booking {} CANCELLED, reversovano {}€, novo usedAmount={}€)",
                            LogUtils.maskVoucherCode(v.getCode()), saved.getBookingRef(), saved.getVoucherDiscount(), v.getUsedAmount());
                } else if ((status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED)
                        && oldStatus == BookingStatus.CANCELLED) {
                    // Un-cancel: rezervacija se vraća u aktivan status, pa vaučer mora
                    // ponovo da se zaključa - inače ostaje ACTIVE (slobodan za drugu
                    // rezervaciju) dok ova rezervacija i dalje nosi popust u ceni, što je
                    // upravo bag koji je prijavljen (otkazano pa opet potvrđeno = vaučer
                    // ostaje "otključan"). Iznos ograničavamo na ono što STVARNO preostaje
                    // na vaučeru (ne slepo vraćamo originalni popust) za slučaj da je neko
                    // drugi u međuvremenu delimično potrošio isti vaučer dok je bio ACTIVE.
                    Integer disc = saved.getVoucherDiscount();
                    if (disc != null && disc > 0) {
                        java.math.BigDecimal remaining = v.getAmount().subtract(v.getUsedAmount())
                                .max(java.math.BigDecimal.ZERO);
                        java.math.BigDecimal relock    = remaining.min(java.math.BigDecimal.valueOf(disc));
                        java.math.BigDecimal newUsed    = v.getUsedAmount().add(relock);
                        v.setUsedAmount(newUsed);
                        v.setUsedInBookingRef(saved.getId());
                        v.setStatus(newUsed.compareTo(v.getAmount()) >= 0 ? VoucherStatus.RESERVED : VoucherStatus.ACTIVE);
                        giftVoucherRepository.save(v);
                        log.info("[Voucher] {} → {} (booking {} vraćen iz CANCELLED u {}, ponovo zaključano {}€ od originalnih {}€, novo usedAmount={}€)",
                                LogUtils.maskVoucherCode(v.getCode()), v.getStatus(), saved.getBookingRef(), status, relock, disc, newUsed);
                    }
                }
                // Ostali prelazi (npr. PENDING → CONFIRMED bez prethodnog CANCELLED) - nema promene
            });
        }

        // Snapshot agencije se pravi tek pri potvrdi - do tada admin može promeniti agenciju na terminu.
        if (status == BookingStatus.CONFIRMED && saved.getAgencyIdSnapshot() == null) {
            availableDateRepository.findByBookingId(id).ifPresent(date -> {
                if (date.getAgency() != null) {
                    saved.setAgencyIdSnapshot(date.getAgency().getId());
                    saved.setAgencyNameSnapshot(date.getAgency().getName());
                    bookingRepository.save(saved);
                }
            });
        }

        // Mejl se šalje kroz event - garantuje slanje tek POSLE commit-a
        if (status == BookingStatus.CONFIRMED) {
            eventPublisher.publishEvent(new BookingEmailEvent(saved, BookingEmailEvent.Type.BOOKING_CONFIRMED));
        } else if (status == BookingStatus.CANCELLED && oldStatus == BookingStatus.CONFIRMED) {
            eventPublisher.publishEvent(new BookingEmailEvent(saved, BookingEmailEvent.Type.BOOKING_CANCELLED));
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
    public AdminBookingResponse setDestination(Long id, String destination, boolean force) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija ne postoji: " + id));

        String trimmed = (destination == null) ? null : destination.strip();
        boolean isChange = trimmed != null && !trimmed.isEmpty()
                && booking.getAssignedDestination() != null
                && !trimmed.equals(booking.getAssignedDestination());

        if (isChange && booking.getRevealSentAt() != null && !force) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Reveal je već poslat — koristite force=true da promenite destinaciju");
        }

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
    public CustomDateInquiryResponse updateInquiryDate(Long id, LocalDate desiredDepartureDate, Integer nights) {
        return inquiryService.updateDate(id, desiredDepartureDate, nights);
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

        if (req.agencyId() != null) {
            Agency agency = agencyRepository.findById(req.agencyId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Agencija nije pronađena: " + req.agencyId()));
            date.setAgency(agency);
        }

        AvailableDate saved = availableDateRepository.save(date);
        log.info("[ADMIN] Privatni termin kreiran za upit id={} | {} → {} | token={} | {}€/os | expiresAt={}",
                inquiryId, depDate, retDate, LogUtils.maskToken(saved.getPrivateToken()), req.pricePerPerson(), saved.getExpiresAt());

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

        // Auto-send ako je uslov ispunjen (RevealEvent za non-box, revealSentAt za box).
        // Pravilo je centralizovano u autoSender.canReceive - isti izvor istine kao
        // scheduler retry, da ne bi divergirali dva mesta iste odluke.
        boolean sent = confirmationDocumentAutoSender.sendIfReadyAndPending(saved);
        if (sent) {
            log.info("[ConfirmationDocument] Uploadovan i odmah poslat za {}", saved.getBookingRef());
        } else if (saved.getConfirmationSentAt() == null) {
            log.info("[ConfirmationDocument] Uploadovan za {} - ceka se uslov reveal-a (RevealEvent za non-box, revealSentAt za box)", saved.getBookingRef());
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
        // Pravilo (isti izvor istine kao ConfirmationDocumentAutoSender.canReceive):
        //   - non-box: RevealEvent (kupac je kliknuo reveal link i vec zna grad)
        //   - box:     revealSentAt (kutija je stigla na T-5..T-3 sa destinacijom,
        //              nema smisla cekati klik jer nije obavezan)
        if (!confirmationDocumentAutoSender.canReceive(booking)) {
            String reason = Boolean.TRUE.equals(booking.getHasRevealBox())
                    ? "Reveal mejl jos nije poslat. Dokument sadrži destinaciju i ne sme stići pre toga."
                    : "Kupac još nije otvorio reveal. Dokument sadrži destinaciju i ne sme stići pre toga.";
            throw new ResponseStatusException(HttpStatus.CONFLICT, reason);
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

    // ══ AGENCIJE ═════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<AgencyResponse> getAllAgencies() {
        return agencyRepository.findAllByOrderByNameAsc().stream()
                .map(this::toAgencyResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AgencyResponse> getActiveAgencies() {
        return agencyRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toAgencyResponse).toList();
    }

    @Transactional
    public AgencyResponse createAgency(AgencyRequest req) {
        Agency a = new Agency();
        a.setName(req.name().trim());
        a.setContactName(req.contactName() != null ? req.contactName().trim() : null);
        a.setContactEmail(req.contactEmail() != null ? req.contactEmail().trim().toLowerCase() : null);
        a.setContactPhone(req.contactPhone() != null ? req.contactPhone().trim() : null);
        a.setNotes(req.notes() != null ? req.notes().trim() : null);
        a.setActive(true);
        Agency saved = agencyRepository.save(a);
        log.info("[ADMIN] Kreirana agencija id={} name={}", saved.getId(), saved.getName());
        return toAgencyResponse(saved);
    }

    @Transactional
    public AgencyResponse updateAgency(Long id, AgencyRequest req) {
        Agency a = agencyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Agencija nije pronađena: " + id));
        a.setName(req.name().trim());
        a.setContactName(req.contactName() != null ? req.contactName().trim() : null);
        a.setContactEmail(req.contactEmail() != null ? req.contactEmail().trim().toLowerCase() : null);
        a.setContactPhone(req.contactPhone() != null ? req.contactPhone().trim() : null);
        a.setNotes(req.notes() != null ? req.notes().trim() : null);
        return toAgencyResponse(agencyRepository.save(a));
    }

    @Transactional
    public AgencyResponse toggleAgencyActive(Long id) {
        Agency a = agencyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Agencija nije pronađena: " + id));
        a.setActive(!a.getActive());
        log.info("[ADMIN] Agencija id={} active={}", id, a.getActive());
        return toAgencyResponse(agencyRepository.save(a));
    }

    @Transactional
    public void assignAgencyToDate(Long dateId, Long agencyId) {
        AvailableDate date = findDateOrThrow(dateId);
        if (agencyId == null) {
            date.setAgency(null);
        } else {
            Agency agency = agencyRepository.findById(agencyId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Agencija nije pronađena: " + agencyId));
            date.setAgency(agency);
        }
        availableDateRepository.save(date);
    }

    private AgencyResponse toAgencyResponse(Agency a) {
        return AgencyResponse.builder()
                .id(a.getId())
                .name(a.getName())
                .contactName(a.getContactName())
                .contactEmail(a.getContactEmail())
                .contactPhone(a.getContactPhone())
                .notes(a.getNotes())
                .active(a.getActive())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgencyEarningsResponse> getAgencyEarnings() {
        // [agencyId, agencyName, dateId, depDate, retDate, airport, sumRevenue, sumCost, sumTravelers]
        List<Object[]> rows = bookingRepository.findAgencyEarningsAggregated();
        if (rows.isEmpty()) return List.of();

        Map<Long, List<Object[]>> byAgency = rows.stream()
                .collect(Collectors.groupingBy(r -> (Long) r[0]));

        return byAgency.entrySet().stream().map(entry -> {
            List<Object[]> agencyRows = entry.getValue();
            String agencyName = (String) agencyRows.get(0)[1];

            List<AgencyEarningsResponse.TermEarning> termEarnings = agencyRows.stream().map(r -> {
                int travelers = ((Long) r[8]).intValue();
                int paid      = ((Long) r[6]).intValue();
                int cost      = ((Long) r[7]).intValue();
                int voucher   = ((Long) r[9]).intValue();
                int revenue   = paid + voucher;
                return AgencyEarningsResponse.TermEarning.builder()
                        .dateId((Long) r[2])
                        .departureDate(r[3].toString())
                        .returnDate(r[4].toString())
                        .departureAirport((String) r[5])
                        .travelers(travelers)
                        .revenue(revenue)
                        .cost(cost)
                        .profit(revenue - cost)
                        .voucher(voucher)
                        .build();
            }).toList();

            int totalTravelers = termEarnings.stream().mapToInt(AgencyEarningsResponse.TermEarning::getTravelers).sum();
            int totalRevenue = termEarnings.stream().mapToInt(AgencyEarningsResponse.TermEarning::getRevenue).sum();
            int totalCost = termEarnings.stream().mapToInt(AgencyEarningsResponse.TermEarning::getCost).sum();
            int totalVoucher = termEarnings.stream().mapToInt(AgencyEarningsResponse.TermEarning::getVoucher).sum();

            return AgencyEarningsResponse.builder()
                    .agencyId(entry.getKey())
                    .agencyName(agencyName)
                    .totalTerms(agencyRows.size())
                    .totalTravelers(totalTravelers)
                    .totalRevenue(totalRevenue)
                    .totalCost(totalCost)
                    .totalProfit(totalRevenue - totalCost)
                    .totalVoucher(totalVoucher)
                    .terms(termEarnings)
                    .build();
        }).toList();
    }

    @Transactional
    public AdminBookingResponse setAgencyCost(Long bookingId, Integer agencyCost) {
        if (agencyCost != null && agencyCost < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trošak agencije ne može biti negativan");
        }
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija ne postoji: " + bookingId));
        booking.setAgencyCost(agencyCost);
        Booking saved = bookingRepository.save(booking);
        log.info("[ADMIN] Agency cost za {} → {}€", saved.getBookingRef(), agencyCost);
        return adminBookingMapper.toResponse(saved);
    }

    // ══ AGENCIJSKI OBRACUN (per-booking faktura) ═════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public AgencySettlementResponse previewAgencySettlement(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija ne postoji: " + bookingId));
        return agencySettlementCalculator.calculate(booking);
    }

    @Override
    @Transactional
    public AgencySettlementResponse setAgencyCosts(Long bookingId, AgencyCostsRequest req) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija ne postoji: " + bookingId));

        // Zakljucana rezervacija - jednom fakturisana ne moze da menja troskove
        // (mora ici storno/korekcija, ne tiho prepisivanje).
        if (booking.getSettlementStatus() == SettlementStatus.INVOICED
                || booking.getSettlementStatus() == SettlementStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Rezervacija je vec fakturisana - promena troskova zahteva rucnu korekciju.");
        }

        validateNonNegative("flightAgencyCost", req.getFlightAgencyCost());
        validateNonNegative("hotelAgencyCost", req.getHotelAgencyCost());
        validateNonNegative("accommodationUpgradeAgencyCost", req.getAccommodationUpgradeAgencyCost());
        validateNonNegative("breakfastAgencyCost", req.getBreakfastAgencyCost());
        validateNonNegative("seatsTogetherAgencyCost", req.getSeatsTogetherAgencyCost());
        validateNonNegative("cabinSuitcaseAgencyCost", req.getCabinSuitcaseAgencyCost());
        validateNonNegative("insuranceAgencyCost", req.getInsuranceAgencyCost());

        // BASE_PACKAGE: flight + hotel = base agencyCost. Oba se moraju uneti
        // zajedno ili nijedno.
        updateBaseIfPresent(booking, req.getFlightAgencyCost(), req.getHotelAgencyCost());

        updateAgencyCostIfPresent(booking, ItemType.ACCOMMODATION_UPGRADE, req.getAccommodationUpgradeAgencyCost());
        updateAgencyCostIfPresent(booking, ItemType.BREAKFAST,             req.getBreakfastAgencyCost());
        updateAgencyCostIfPresent(booking, ItemType.SEATS_TOGETHER,        req.getSeatsTogetherAgencyCost());
        updateAgencyCostIfPresent(booking, ItemType.CABIN_SUITCASE,        req.getCabinSuitcaseAgencyCost());
        updateAgencyCostIfPresent(booking, ItemType.INSURANCE,             req.getInsuranceAgencyCost());

        // Pusti kalkulator da odluci status - ako su svi troskovi tu i nema negativne
        // marze, prelazak na READY_FOR_INVOICE.
        AgencySettlementResponse preview = agencySettlementCalculator.calculate(booking);
        if (preview.isReadyForInvoice()
                && booking.getSettlementStatus() == SettlementStatus.NEEDS_COSTS) {
            booking.setSettlementStatus(SettlementStatus.READY_FOR_INVOICE);
        } else if (!preview.isReadyForInvoice()
                && booking.getSettlementStatus() == SettlementStatus.READY_FOR_INVOICE) {
            // ako je admin obrisao trosak posle sto je vec bilo READY, vraca u NEEDS_COSTS
            booking.setSettlementStatus(SettlementStatus.NEEDS_COSTS);
        }

        bookingRepository.save(booking);
        log.info("[ADMIN] Troskovi agencije azurirani za {} -> settlementStatus={}",
                booking.getBookingRef(), booking.getSettlementStatus());
        return preview;
    }

    private void validateNonNegative(String field, java.math.BigDecimal v) {
        if (v != null && v.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trosak '" + field + "' ne sme biti negativan");
        }
    }

    private void updateBaseIfPresent(Booking booking,
                                     java.math.BigDecimal flight,
                                     java.math.BigDecimal hotel) {
        if (flight == null && hotel == null) return; // nema izmene
        if (flight == null || hotel == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "flightAgencyCost i hotelAgencyCost moraju biti poslati zajedno");
        }
        BookingFinancialItem base = findOrThrow(booking, ItemType.BASE_PACKAGE);
        base.setFlightAgencyCost(flight);
        base.setHotelAgencyCost(hotel);
        base.setAgencyCost(flight.add(hotel));
    }

    private void updateAgencyCostIfPresent(Booking booking, ItemType type, java.math.BigDecimal cost) {
        if (cost == null) return;
        BookingFinancialItem item = booking.getFinancialItems().stream()
                .filter(i -> i.getItemType() == type)
                .findFirst()
                .orElse(null);
        if (item == null) {
            // Booking ne sadrzi tu stavku (npr. nema doručka) - tiho preskoci umesto 400,
            // admin panel ionako prikazuje samo unose za stavke koje booking ima.
            log.warn("[ADMIN] Ignorisan trosak za {} na bookingu {} - stavka ne postoji",
                    type, booking.getBookingRef());
            return;
        }
        item.setAgencyCost(cost);
    }

    private BookingFinancialItem findOrThrow(Booking booking, ItemType type) {
        return booking.getFinancialItems().stream()
                .filter(i -> i.getItemType() == type)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Rezervacija nema stavku " + type + " - moguca korupcija podataka."));
    }

    // ══ FINALIZE + STATUS + DASHBOARD ════════════════════════════════════════

    @Override
    @Transactional
    public AgencySettlementResponse finalizeAgencyInvoice(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija ne postoji: " + bookingId));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Otkazana rezervacija ne moze da se fakturise.");
        }
        if (booking.getStatus() == BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Nepotvrdjena rezervacija (PENDING) ne moze da se fakturise - kupac jos nije platio.");
        }
        if (booking.getSettlementStatus() == SettlementStatus.INVOICED
                || booking.getSettlementStatus() == SettlementStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Rezervacija je vec fakturisana (broj " + booking.getAgencyInvoiceNumber()
                    + "). Storno zahteva rucnu korekciju.");
        }

        AgencySettlementResponse preview = agencySettlementCalculator.calculate(booking);
        if (!preview.isReadyForInvoice()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Obracun nije spreman za fakturu: " + String.join("; ", preview.getValidationErrors()));
        }

        String invoiceNumber = generateAgencyInvoiceNumber();
        booking.setAgencyInvoiceNumber(invoiceNumber);
        booking.setAgencyInvoicedAt(LocalDateTime.now());
        booking.setSettlementStatus(SettlementStatus.INVOICED);
        Booking saved = bookingRepository.save(booking);

        log.info("[ADMIN] Faktura {} generisana za {} (agencija {}, Escapii duguje: {}€, agencija duguje: {}€)",
                invoiceNumber, saved.getBookingRef(), saved.getAgencyNameSnapshot(),
                preview.getNetSettlement().signum() < 0 ? preview.getNetSettlement().abs() : "0",
                preview.getNetSettlement().signum() > 0 ? preview.getNetSettlement() : "0");
        return agencySettlementCalculator.calculate(saved);
    }

    private String generateAgencyInvoiceNumber() {
        int year = java.time.LocalDate.now().getYear();
        com.escapii.model.AgencyInvoiceSequence seq = agencyInvoiceSequenceRepository.findByYear(year)
                .orElseGet(() -> agencyInvoiceSequenceRepository.save(
                        new com.escapii.model.AgencyInvoiceSequence(year)));
        seq.setLastSeq(seq.getLastSeq() + 1);
        agencyInvoiceSequenceRepository.save(seq);
        return "ESC-AG-" + year + "-" + String.format("%04d", seq.getLastSeq());
    }

    @Override
    @Transactional
    public AgencySettlementResponse updateSettlementStatus(Long bookingId, SettlementStatus newStatus) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija ne postoji: " + bookingId));

        SettlementStatus current = booking.getSettlementStatus();
        if (current == newStatus) {
            return agencySettlementCalculator.calculate(booking);
        }

        boolean allowed = switch (current) {
            case NEEDS_COSTS       -> newStatus == SettlementStatus.READY_FOR_INVOICE;
            case READY_FOR_INVOICE -> newStatus == SettlementStatus.NEEDS_COSTS;
            case INVOICED          -> newStatus == SettlementStatus.PAID
                                   || newStatus == SettlementStatus.READY_FOR_INVOICE;
            case PAID              -> newStatus == SettlementStatus.INVOICED;
        };
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Nedozvoljen prelaz " + current + " -> " + newStatus);
        }

        booking.setSettlementStatus(newStatus);
        if (newStatus == SettlementStatus.PAID) {
            booking.setAgencyPaidAt(LocalDateTime.now());
        } else if (newStatus == SettlementStatus.INVOICED && current == SettlementStatus.PAID) {
            // Rollback iz PAID: brisemo paidAt ali invoice broj/datum ostaju
            booking.setAgencyPaidAt(null);
        } else if (newStatus == SettlementStatus.READY_FOR_INVOICE && current == SettlementStatus.INVOICED) {
            // Storno fakture - brisemo invoice broj i datum. Broj se ne recikla
            // (sekvenca ostaje inkrementovana) da revizija vidi rupu i pita zasto.
            log.warn("[ADMIN] Storno fakture {} za {} - broj se ne reciklira (revizija).",
                    booking.getAgencyInvoiceNumber(), booking.getBookingRef());
            booking.setAgencyInvoiceNumber(null);
            booking.setAgencyInvoicedAt(null);
        }
        Booking saved = bookingRepository.save(booking);
        log.info("[ADMIN] Settlement status {} -> {} za {}",
                current, newStatus, saved.getBookingRef());
        return agencySettlementCalculator.calculate(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.escapii.dto.AgencyDashboardRow> agencyDashboard(
            Long agencyId, java.time.LocalDate from, java.time.LocalDate to,
            SettlementStatus status) {
        List<Booking> bookings = bookingRepository.findForAgencyDashboard(agencyId, from, to, status);
        return bookings.stream()
                .map(this::toDashboardRow)
                .toList();
    }

    private com.escapii.dto.AgencyDashboardRow toDashboardRow(Booking b) {
        AgencySettlementResponse s = agencySettlementCalculator.calculate(b);
        return com.escapii.dto.AgencyDashboardRow.builder()
                .bookingId(b.getId())
                .bookingRef(b.getBookingRef())
                .agencyId(b.getAgencyIdSnapshot())
                .agencyName(b.getAgencyNameSnapshot())
                .departureDate(b.getSelectedDate() != null ? b.getSelectedDate().getDepartureDate() : null)
                .returnDate(b.getSelectedDate() != null ? b.getSelectedDate().getReturnDate() : null)
                .customerName(b.getFirstName() + " " + b.getLastName())
                .numberOfTravelers(b.getNumberOfTravelers())
                .settlementStatus(b.getSettlementStatus())
                .agencyInvoiceNumber(b.getAgencyInvoiceNumber())
                .agencyInvoicedAt(b.getAgencyInvoicedAt())
                .agencyPaidAt(b.getAgencyPaidAt())
                .grossBookingValue(s.getGrossBookingValue())
                .voucherAmount(s.getVoucherAmount())
                .escapiiEarnings(s.getEscapiiEarnings())
                .netSettlement(s.getNetSettlement())
                .agencyRetainedAmount(s.getAgencyRetainedAmount())
                .readyForInvoice(s.isReadyForInvoice())
                .build();
    }

    // ══ HELPERS ══════════════════════════════════════════════════════════════

    private AvailableDate findDateOrThrow(Long id) {
        return availableDateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Termin ne postoji: " + id));
    }
}
