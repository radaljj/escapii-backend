package com.escapii.service.impl;

import com.escapii.dto.GiftVoucherRequest;
import com.escapii.dto.GiftVoucherResponse;
import com.escapii.dto.GiftVoucherRevealResponse;
import com.escapii.dto.GiftVoucherValidateRequest;
import com.escapii.dto.GiftVoucherValidateResponse;
import com.escapii.model.GiftVoucher;
import com.escapii.model.VoucherStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.service.GiftVoucherService;
import com.escapii.service.email.GiftVoucherEmailService;
import com.escapii.service.voucher.VoucherData;
import com.escapii.service.voucher.VoucherPdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GiftVoucherServiceImpl implements GiftVoucherService {

    private final GiftVoucherRepository voucherRepository;
    private final BookingRepository bookingRepository;
    private final GiftVoucherEmailService emailService;
    private final VoucherPdfService voucherPdfService;

    /**
     * Skup karaktera za generisanje koda.
     * Namerno isključeni: 0 (nula), O (veliko o), 1 (jedan), I (veliko i), L (malo l)
     * - da bi kod bio čitljiv i bez zabune pri unosu.
     */
    private static final String CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int SEGMENT_LENGTH = 4;
    private static final int SEGMENTS = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ── Javni endpointi ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public GiftVoucherResponse createVoucher(GiftVoucherRequest req) {
        GiftVoucher v = new GiftVoucher();
        v.setCode(generateUniqueCode());
        v.setAmount(BigDecimal.valueOf(req.amount()));
        v.setBuyerEmail(req.buyerEmail().trim().toLowerCase());
        v.setBuyerName(req.buyerName() != null ? req.buyerName().trim() : null);
        v.setGiftMessage(req.giftMessage() != null ? req.giftMessage().trim() : null);

        GiftVoucher saved = voucherRepository.save(v);
        log.info("[GiftVoucher] Nov vaučer: id={}, amount={}, buyerEmail={}",
                saved.getId(), saved.getAmount(), saved.getBuyerEmail());

        emailService.sendTeamAlert(saved);

        return toResponseWithoutCode(saved);
    }

    @Override
    @Transactional
    public GiftVoucherValidateResponse validate(GiftVoucherValidateRequest req) {
        String code = req.code().trim().toUpperCase();

        return voucherRepository.findByCode(code)
                .map(v -> {
                    if (v.getStatus() != VoucherStatus.ACTIVE) {
                        log.info("[GiftVoucher] Validacija neuspešna (status={}) za kod={}", v.getStatus(), maskCode(code));
                        return GiftVoucherValidateResponse.invalid();
                    }
                    if (v.getExpiresAt() != null && LocalDateTime.now().isAfter(v.getExpiresAt())) {
                        log.info("[GiftVoucher] Validacija neuspešna (expired) za kod={}", maskCode(code));
                        v.setStatus(VoucherStatus.EXPIRED);
                        voucherRepository.save(v);
                        return GiftVoucherValidateResponse.invalid();
                    }
                    BigDecimal remaining = v.getAmount().subtract(v.getUsedAmount());
                    log.info("[GiftVoucher] Validacija uspešna za kod={}, remaining={}", maskCode(code), remaining);
                    return GiftVoucherValidateResponse.ok(remaining);
                })
                .orElseGet(() -> {
                    log.info("[GiftVoucher] Validacija neuspešna (not found) za kod={}", maskCode(code));
                    return GiftVoucherValidateResponse.invalid();
                });
    }

    @Override
    @Transactional
    public GiftVoucherRevealResponse reveal(String code) {
        if (code == null || code.isBlank()) return GiftVoucherRevealResponse.invalid();
        String normalizedCode = code.trim().toUpperCase();

        return voucherRepository.findByCode(normalizedCode)
                .map(v -> {
                    if (v.getStatus() != VoucherStatus.ACTIVE) {
                        log.info("[GiftVoucher] Reveal neuspešan (status={}) za kod={}", v.getStatus(), maskCode(normalizedCode));
                        return GiftVoucherRevealResponse.invalid();
                    }
                    if (v.getExpiresAt() != null && LocalDateTime.now().isAfter(v.getExpiresAt())) {
                        log.info("[GiftVoucher] Reveal neuspešan (expired) za kod={}", maskCode(normalizedCode));
                        v.setStatus(VoucherStatus.EXPIRED);
                        voucherRepository.save(v);
                        return GiftVoucherRevealResponse.invalid();
                    }
                    log.info("[GiftVoucher] Reveal uspešan za kod={}", maskCode(normalizedCode));
                    return GiftVoucherRevealResponse.ok(v);
                })
                .orElseGet(() -> {
                    log.info("[GiftVoucher] Reveal neuspešan (not found) za kod={}", maskCode(normalizedCode));
                    return GiftVoucherRevealResponse.invalid();
                });
    }

    // ── Admin endpointi ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<GiftVoucherResponse> getAllVouchers() {
        return voucherRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(GiftVoucherResponse::new)
                .toList();
    }

    @Override
    @Transactional
    public GiftVoucherResponse activateVoucher(Long id) {
        GiftVoucher v = findOrThrow(id);
        if (v.getStatus() != VoucherStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vaučer id=" + id + " nije u PENDING statusu (trenutno: " + v.getStatus() + ")");
        }
        v.setStatus(VoucherStatus.ACTIVE);
        v.setActivatedAt(LocalDateTime.now());
        v.setExpiresAt(LocalDateTime.now().plusYears(1));
        GiftVoucher saved = voucherRepository.save(v);
        log.info("[GiftVoucher] Vaučer id={} aktiviran, generišem PDF i šaljem na {}", id, saved.getBuyerEmail());

        // Generiši PDF vaučer i pošalji kupcu (asinhrono)
        try {
            VoucherData pdfData = VoucherData.of(
                saved.getAmount().intValueExact(),
                saved.getCode(),
                LocalDate.now(),
                saved.getBuyerName(),
                saved.getGiftMessage()
            );
            byte[] pdf = voucherPdfService.generate(pdfData);
            emailService.sendVoucherPdfToBuyer(saved, pdf);
        } catch (Exception e) {
            // PDF generisanje ne sme blokirati aktivaciju - logujemo grešku
            log.error("[GiftVoucher] PDF generisanje/slanje nije uspelo za vaučer id={}: {}", id, e.getMessage(), e);
        }

        return new GiftVoucherResponse(saved);
    }

    @Override
    @Transactional
    public GiftVoucherResponse markUsed(Long id, Long bookingRef) {
        GiftVoucher v = findOrThrow(id);
        if (v.getStatus() != VoucherStatus.ACTIVE && v.getStatus() != VoucherStatus.RESERVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vaučer id=" + id + " nije u ACTIVE/RESERVED statusu (trenutno: " + v.getStatus() + ")");
        }

        // usedAmount je već ispravno postavljen u createBooking() kad je vaučer primenjen -
        // ovde se NE sme ponovo dodavati popust, samo finalizujemo status.
        if (bookingRef != null) {
            v.setUsedInBookingRef(bookingRef);
        }

        // Vaučer postaje USED tek kad je u potpunosti potrošen
        if (v.getUsedAmount().compareTo(v.getAmount()) >= 0) {
            v.setStatus(VoucherStatus.USED);
            v.setUsedAt(LocalDateTime.now());
            log.info("[GiftVoucher] Vaučer id={} u potpunosti iskorišćen ({}€ od {}€) u booking ref={}",
                    id, v.getUsedAmount(), v.getAmount(), bookingRef);
        } else {
            // Delimično iskorišćen - ostaje ACTIVE sa preostalim saldom
            v.setStatus(VoucherStatus.ACTIVE);
            BigDecimal remaining = v.getAmount().subtract(v.getUsedAmount());
            log.info("[GiftVoucher] Vaučer id={} delimično iskorišćen ({}€ potrošeno u booking ref={}), preostaje {}€",
                    id, v.getUsedAmount(), bookingRef, remaining);
        }

        return new GiftVoucherResponse(voucherRepository.save(v));
    }

    @Override
    @Transactional
    public GiftVoucherResponse reactivateVoucher(Long id) {
        GiftVoucher v = findOrThrow(id);
        if (v.getStatus() == VoucherStatus.PENDING || v.getStatus() == VoucherStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vaučer id=" + id + " ne može biti reaktiviran (status: " + v.getStatus() + ").");
        }
        VoucherStatus oldStatus = v.getStatus();
        v.setStatus(VoucherStatus.ACTIVE);
        v.setUsedAt(null);
        v.setUsedInBookingRef(null);
        v.setUsedAmount(BigDecimal.ZERO);
        GiftVoucher saved = voucherRepository.save(v);
        log.info("[GiftVoucher] Admin reaktivirao vaučer id={} ({} → ACTIVE)", id, oldStatus);
        return new GiftVoucherResponse(saved);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = buildCode();
            if (!voucherRepository.existsByCode(code)) {
                return code;
            }
            log.warn("[GiftVoucher] Kolizija koda pri generisanju (pokušaj {})", attempt + 1);
        }
        throw new IllegalStateException("Ne može se generisati jedinstven vaučer kod - sistem greška");
    }

    private String buildCode() {
        StringBuilder sb = new StringBuilder("ESC-");
        for (int seg = 0; seg < SEGMENTS; seg++) {
            for (int i = 0; i < SEGMENT_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(SECURE_RANDOM.nextInt(CODE_CHARS.length())));
            }
            if (seg < SEGMENTS - 1) sb.append("-");
        }
        return sb.toString(); // npr. ESC-A3KM-P2HT-X9QR
    }

    private GiftVoucher findOrThrow(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Vaučer sa ID=" + id + " nije pronađen."));
    }

    private String maskCode(String code) {
        if (code == null || code.length() < 4) return "***";
        return "ESC-****-****-" + code.substring(code.lastIndexOf('-') + 1);
    }

    private GiftVoucherResponse toResponseWithoutCode(GiftVoucher v) {
        return new GiftVoucherResponse(
                v.getId(), v.getAmount(), v.getStatus(),
                v.getBuyerEmail(), v.getBuyerName(),
                v.getGiftMessage(), v.getCreatedAt(),
                v.getActivatedAt(), v.getExpiresAt(),
                v.getUsedAt(), v.getUsedInBookingRef(),
                null, // KOD SE NE VRAĆA JAVNO
                v.getUsedAmount(),
                v.getAmount().subtract(v.getUsedAmount()),
                v.getInvoiceNumber(),
                v.getInvoiceSentAt()
        );
    }
}
