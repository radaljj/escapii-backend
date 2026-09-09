package com.escapii.service.impl;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.GiftVoucherRevealResponse;
import com.escapii.mapper.AdminBookingMapper;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.service.GiftTripVoucherService;
import com.escapii.service.email.BookingEmailService;
import com.escapii.service.voucher.TripVoucherData;
import com.escapii.service.voucher.VoucherPdfService;
import com.escapii.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GiftTripVoucherServiceImpl implements GiftTripVoucherService {

    private final BookingRepository bookingRepository;
    private final VoucherPdfService voucherPdfService;
    private final BookingEmailService bookingEmailService;
    private final AdminBookingMapper adminBookingMapper;

    // ── /poklon stranica ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public GiftVoucherRevealResponse reveal(String code) {
        if (code == null || code.isBlank()) return GiftVoucherRevealResponse.invalid();
        String ref = code.trim();

        return bookingRepository.findByBookingRefIgnoreCase(ref)
                .map(b -> {
                    if (!vaucerVazi(b)) {
                        log.info("[PoklonPut] Reveal odbijen za {} (poklon={}, status={})",
                                LogUtils.maskVoucherCode(ref), b.getIsGift(), b.getStatus());
                        return GiftVoucherRevealResponse.invalid();
                    }
                    log.info("[PoklonPut] Reveal uspešan za {}", LogUtils.maskVoucherCode(ref));
                    return GiftVoucherRevealResponse.trip(b, details(b));
                })
                .orElseGet(() -> {
                    log.info("[PoklonPut] Reveal: nema rezervacije za {}", LogUtils.maskVoucherCode(ref));
                    return GiftVoucherRevealResponse.invalid();
                });
    }

    /**
     * Vaučer postoji samo za poklon koji je potvrđen (uplata legla). Ostaje i
     * posle puta (COMPLETED) - stranica je tada uspomena, ne rizik. PENDING i
     * CANCELLED ne daju ništa: nema šta da se pokloni.
     */
    static boolean vaucerVazi(Booking b) {
        return Boolean.TRUE.equals(b.getIsGift())
            && (b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.COMPLETED);
    }

    private static GiftVoucherRevealResponse.TripDetails details(Booking b) {
        TripVoucherData d = TripVoucherData.from(b);
        return new GiftVoucherRevealResponse.TripDetails(
                d.departureDate(), d.returnDate(), d.nights(), d.travelers(),
                d.airportCode(), d.airportCity(), d.airportName(),
                d.passengers(), b.getGiftRecipientName());
    }

    // ── admin: pošalji ponovo ────────────────────────────────────────────────

    @Override
    @Transactional
    public AdminBookingResponse resend(Long bookingId) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija nije pronađena: " + bookingId));
        if (!Boolean.TRUE.equals(b.getIsGift())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Rezervacija nije poklon - nema vaučera za putovanje.");
        }
        if (!vaucerVazi(b)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vaučer se šalje tek kad je rezervacija potvrđena (uplata legla). Trenutni status: " + b.getStatus());
        }

        // PDF se pravi OVDE, ne u mejl servisu: automatska potvrda sme da ode i bez
        // priloga kad PDF pukne, ali admin koji klikne "pošalji ponovo" mora da vidi
        // da PDF nije napravljen - inače bi kupac drugi put dobio potvrdu bez vaučera.
        byte[] pdf;
        try {
            pdf = voucherPdfService.generateTrip(TripVoucherData.from(b));
        } catch (Exception e) {
            log.error("[PoklonPut] PDF vaučera nije generisan za {}: {}", b.getBookingRef(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "PDF vaučera nije generisan: " + e.getMessage());
        }
        if (!bookingEmailService.sendBookingConfirmedNow(b, pdf)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Slanje nije uspelo - proveri log i SMTP, pa pokušaj ponovo.");
        }
        log.info("[PoklonPut] Potvrda sa vaučerom ručno ponovo poslata za {}", b.getBookingRef());
        return adminBookingMapper.toResponse(b);
    }
}
