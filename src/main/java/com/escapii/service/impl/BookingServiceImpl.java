package com.escapii.service.impl;

import com.escapii.dto.BookingRequest;
import com.escapii.dto.BookingResponse;
import com.escapii.dto.BookingStatusResponse;
import com.escapii.dto.PricePreviewResponse;
import com.escapii.mapper.BookingMapper;
import com.escapii.model.AccommodationType;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.Destination;
import com.escapii.model.GiftVoucher;
import com.escapii.model.VoucherStatus;
import java.util.List;

import com.escapii.model.PassengerInfo;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.DestinationRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.service.BookingService;
import com.escapii.service.email.BookingEmailService;
import com.escapii.service.PriceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository       bookingRepository;
    private final AvailableDateRepository availableDateRepository;
    private final DestinationRepository   destinationRepository;
    private final GiftVoucherRepository   giftVoucherRepository;
    private final PriceCalculator         priceCalculator;
    private final BookingEmailService     bookingEmailService;
    private final BookingMapper           bookingMapper;

    @Override
    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        request.normalize();

        // ── Anti-bot provjere ─────────────────────────────────────────

        // 0a. Honeypot — mora biti null ili prazan
        if (request.getWebsite() != null && !request.getWebsite().isBlank()) {
            log.warn("[AntiBot] Honeypot popunjen — odbijen zahtev (website='{}')", request.getWebsite());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nevažeći zahtev");
        }

        // 0b. Timing — forma popunjena za manje od 4 sekunde = bot
        if (request.getFormDuration() != null && request.getFormDuration() < 4) {
            log.warn("[AntiBot] Forma popunjena za {}s — odbijen zahtev", request.getFormDuration());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nevažeći zahtev");
        }

        // 0c. Duplikat — isti email + isti termin u poslednjih 24h
        if (bookingRepository.existsDuplicateBooking(
                request.getEmail(),
                request.getSelectedDateId(),
                java.time.LocalDateTime.now().minusHours(24))) {
            log.warn("[AntiBot] Duplikat booking — email='{}' dateId={}", request.getEmail(), request.getSelectedDateId());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Već imate aktivnu rezervaciju za ovaj termin sa ovom email adresom.");
        }

        AvailableDate date  = findActiveDateOrThrow(request.getSelectedDateId());

        // 0. Datum polaska mora biti u budućnosti
        if (!date.getDepartureDate().isAfter(java.time.LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Datum polaska mora biti u budućnosti");
        }

        // 1. Aerodrom u zahtevu mora da odgovara aerodromu termina
        if (!request.getDepartureAirport().equalsIgnoreCase(date.getDepartureAirport())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aerodrom polaska ne odgovara izabranom terminu");
        }

        // 2. Dovoljno slobodnih mesta
        if (date.getAvailableSlots() < request.getNumberOfTravelers()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Nema dovoljno slobodnih mesta (dostupno: " + date.getAvailableSlots() + ")");
        }

        // 3. Broj putnika mora da odgovara broju unetih putnika
        if (request.getPassengers().size() != request.getNumberOfTravelers()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Broj putnika (" + request.getNumberOfTravelers() +
                    ") ne odgovara broju unetih putnika (" + request.getPassengers().size() + ")");
        }

        // 4. Broj kofera ne sme biti veći od broja putnika
        if (request.getCabinSuitcaseCount() > request.getNumberOfTravelers()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Broj kofera ne sme biti veći od broja putnika");
        }

        Destination   excl1 = resolveDestination(request.getExcludedDestination1Id());
        Destination   excl2 = resolveDestination(request.getExcludedDestination2Id());
        Destination   excl3 = resolveDestination(request.getExcludedDestination3Id());
        Destination   excl4 = resolveDestination(request.getExcludedDestination4Id());
        int exclusionCount  = countNonNull(excl1, excl2, excl3, excl4);

        PricePreviewResponse price = priceCalculator.calculate(
                date, request.getNumberOfTravelers(), request.getAccommodationType(),
                exclusionCount, request.getCabinSuitcaseCount(),
                request.isHasInsurance(), request.isHasBreakfast(), request.isHasSeatsTogether()
        );

        Booking booking = buildBooking(request, date, excl1, excl2, excl3, excl4, exclusionCount, price);

        // 5. Primeni vaučer popust (ako postoji validan aktivni vaučer)
        GiftVoucher appliedVoucher = null;
        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            String code = request.getVoucherCode().trim().toUpperCase();
            GiftVoucher voucher = giftVoucherRepository.findByCode(code).orElse(null);
            if (voucher != null
                    && voucher.getStatus() == VoucherStatus.ACTIVE
                    && (voucher.getExpiresAt() == null || voucher.getExpiresAt().isAfter(java.time.LocalDateTime.now()))) {
                int discount = voucher.getAmount().intValue();
                booking.setAppliedVoucherCode(code);
                booking.setVoucherDiscount(discount);
                booking.setTotalPriceAll(Math.max(0, booking.getTotalPriceAll() - discount));
                appliedVoucher = voucher;
                log.info("[Booking] Primenjen vaučer {} ({}€) — nova ukupna cena: {}€",
                        code, discount, booking.getTotalPriceAll());
            } else {
                log.warn("[Booking] Vaučer kod '{}' nije validan ili nije aktivan — ignorisan", code);
            }
        }

        Booking saved = bookingRepository.save(booking);

        // Označi vaučer kao RESERVED — blokiran za dalje korišćenje,
        // ali prelazi u USED tek kad rezervacija postane CONFIRMED
        if (appliedVoucher != null) {
            appliedVoucher.setStatus(VoucherStatus.RESERVED);
            appliedVoucher.setUsedInBookingRef(saved.getId());
            // usedAt ostaje null dok rezervacija nije CONFIRMED
            giftVoucherRepository.save(appliedVoucher);
            log.info("[Voucher] {} → RESERVED za booking {}", appliedVoucher.getCode(), saved.getBookingRef());
        }

        log.info("[Booking] Kreiran {} | {} put. | aerodrom {} | termin {}→{}",
                saved.getBookingRef(), saved.getNumberOfTravelers(),
                saved.getDepartureAirport(),
                date.getDepartureDate(), date.getReturnDate());

        bookingEmailService.sendTeamNotification(saved);
        bookingEmailService.sendCustomerConfirmation(saved);

        return bookingMapper.toResponse(saved);
    }

    @Override
    public PricePreviewResponse previewPrice(
            Long selectedDateId, int n, AccommodationType accommodationType, int exclusionCount,
            int cabinSuitcaseCount, boolean hasInsurance, boolean hasBreakfast, boolean hasSeatsTogether
    ) {
        AvailableDate date = availableDateRepository.findById(selectedDateId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Termin ne postoji: " + selectedDateId));

        // accommodationType can be null for preview (default = STANDARD)
        AccommodationType accomType = accommodationType != null ? accommodationType : AccommodationType.STANDARD;
        return priceCalculator.calculate(date, n, accomType, exclusionCount,
                cabinSuitcaseCount, hasInsurance, hasBreakfast, hasSeatsTogether);
    }

    @Override
    public BookingStatusResponse lookupStatus(String bookingRef, String lastName) {
        Booking b = bookingRepository.findByRefAndLastName(
                        bookingRef.trim(), lastName.trim())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Rezervacija nije pronađena. Proverite broj rezervacije i prezime pod kojim je rezervacija napravljena."));

        List<String> names = b.getPassengers().stream()
                .map(PassengerInfo::getName)
                .toList();

        return BookingStatusResponse.builder()
                .bookingRef(b.getBookingRef())
                .status(b.getStatus())
                .firstName(b.getFirstName())
                .lastName(b.getLastName())
                .departureAirport(b.getDepartureAirport())
                .departureDate(b.getSelectedDate().getDepartureDate())
                .returnDate(b.getSelectedDate().getReturnDate())
                .numberOfTravelers(b.getNumberOfTravelers())
                .passengerNames(names)
                .build();
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private AvailableDate findActiveDateOrThrow(Long id) {
        AvailableDate date = availableDateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Izabrani termin ne postoji: " + id));

        if (!Boolean.TRUE.equals(date.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Izabrani termin nije aktivan");
        }
        return date;
    }

    private Destination resolveDestination(Long id) {
        if (id == null) return null;
        return destinationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Destinacija ne postoji: " + id));
    }

    private int countNonNull(Object... items) {
        int count = 0;
        for (Object item : items) if (item != null) count++;
        return count;
    }

    private Booking buildBooking(
            BookingRequest request, AvailableDate date,
            Destination excl1, Destination excl2, Destination excl3, Destination excl4,
            int exclusionCount, PricePreviewResponse price
    ) {
        Booking b = new Booking();

        b.setDepartureAirport(request.getDepartureAirport());
        b.setNumberOfTravelers(request.getNumberOfTravelers());
        b.setSelectedDate(date);

        b.setExcludedDestination1(excl1);
        b.setExcludedDestination2(excl2);
        b.setExcludedDestination3(excl3);
        b.setExcludedDestination4(excl4);
        b.setExclusionCount(exclusionCount);
        b.setExclusionCostEur(price.getExclusionCostFlat());

        b.setAccommodationType(request.getAccommodationType());
        b.setAccommodationExtra(price.getAccommodationExtraPerPerson());

        b.setCabinSuitcaseCount(request.getCabinSuitcaseCount());
        b.setHasInsurance(request.isHasInsurance());
        b.setHasBreakfast(request.isHasBreakfast());
        b.setHasSeatsTogether(request.isHasSeatsTogether());
        b.setHasConnectingFlights(request.isHasConnectingFlights());

        b.setPassengers(request.getPassengers().stream()
                .map(p -> new com.escapii.model.PassengerInfo(
                        p.getName(), p.getGender(), p.getDateOfBirth(), p.getVisaInfo(),
                        p.getHasValidPassport() != null ? p.getHasValidPassport() : true,
                        p.getPassportNumber()))
                .toList());

        b.setBasePricePerPerson(date.getBasePrice());
        b.setTotalPricePerPerson(price.getEurPerPerson());
        b.setTotalPriceAll(price.getTotalEurAll());

        b.setFirstName(request.getFirstName());
        b.setLastName(request.getLastName());
        b.setEmail(request.getEmail());
        b.setPhone(request.getPhone());
        // Ako je korisnik uneo vaučer kod, dodaj ga uz napomenu — admin primenjuje popust ručno
        String notes = request.getNotes();
        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            String voucherNote = "[VAUČER: " + request.getVoucherCode().trim().toUpperCase() + "]";
            notes = (notes != null && !notes.isBlank()) ? notes + " | " + voucherNote : voucherNote;
        }
        b.setNotes(notes);

        return b;
    }
}
