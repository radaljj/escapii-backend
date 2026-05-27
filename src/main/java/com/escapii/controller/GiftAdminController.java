package com.escapii.controller;

import com.escapii.dto.AdminDateResponse;
import com.escapii.dto.CreatePrivateDateRequest;
import com.escapii.dto.GiftTripInquiryResponse;
import com.escapii.dto.GiftVoucherResponse;
import com.escapii.model.InquiryStatus;
import com.escapii.service.GiftTripInquiryService;
import com.escapii.service.GiftVoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Admin endpointi za upravljanje gift sistemom (vaučeri + putovanja).
 * Zaštićeni X-Admin-Key headerom (AdminKeyFilter).
 *
 * ── VAUČERI ──────────────────────────────────────────────────────────────────
 * GET    /api/admin/gifts/vouchers
 * PATCH  /api/admin/gifts/vouchers/{id}/activate   — PENDING → ACTIVE, šalje email primaocu
 * PATCH  /api/admin/gifts/vouchers/{id}/mark-used  — ACTIVE → USED
 *
 * ── GIFT PUTOVANJA ───────────────────────────────────────────────────────────
 * GET    /api/admin/gifts/trips
 * PATCH  /api/admin/gifts/trips/{id}/status?value=IN_REVIEW
 * PATCH  /api/admin/gifts/trips/{id}/price?value=450.00
 */
@RestController
@RequestMapping("/api/admin/gifts")
@RequiredArgsConstructor
public class GiftAdminController {

    private final GiftVoucherService      voucherService;
    private final GiftTripInquiryService  tripService;

    // ══ VAUČERI ══════════════════════════════════════════════════════════════

    /** GET /api/admin/gifts/vouchers — svi vaučeri, sortirano po datumu kreiranja desc. */
    @GetMapping("/vouchers")
    public ResponseEntity<List<GiftVoucherResponse>> getAllVouchers() {
        return ResponseEntity.ok(voucherService.getAllVouchers());
    }

    /**
     * PATCH /api/admin/gifts/vouchers/{id}/activate
     * Aktivira vaučer (PENDING → ACTIVE) i šalje email primaocu sa kodom.
     */
    @PatchMapping("/vouchers/{id}/activate")
    public ResponseEntity<GiftVoucherResponse> activateVoucher(@PathVariable Long id) {
        return ResponseEntity.ok(voucherService.activateVoucher(id));
    }

    /**
     * PATCH /api/admin/gifts/vouchers/{id}/mark-used?bookingRef=123
     * Markira vaučer kao iskorišćen kada admin potvrdi booking sa vaučerom.
     */
    @PatchMapping("/vouchers/{id}/mark-used")
    public ResponseEntity<GiftVoucherResponse> markUsed(
            @PathVariable Long id,
            @RequestParam(required = false) Long bookingRef) {
        return ResponseEntity.ok(voucherService.markUsed(id, bookingRef));
    }

    // ══ GIFT PUTOVANJA ═══════════════════════════════════════════════════════

    /** GET /api/admin/gifts/trips — svi gift trip upiti, sortirano po datumu desc. */
    @GetMapping("/trips")
    public ResponseEntity<List<GiftTripInquiryResponse>> getAllTrips() {
        return ResponseEntity.ok(tripService.getAllInquiries());
    }

    /**
     * PATCH /api/admin/gifts/trips/{id}/status?value=IN_REVIEW
     * Menja status gift trip upita.
     */
    @PatchMapping("/trips/{id}/status")
    public ResponseEntity<GiftTripInquiryResponse> updateTripStatus(
            @PathVariable Long id,
            @RequestParam InquiryStatus value) {
        return ResponseEntity.ok(tripService.updateStatus(id, value));
    }

    /**
     * PATCH /api/admin/gifts/trips/{id}/price?value=450.00
     * Postavlja cenu putovanja (admin unosi nakon formiranja ponude).
     */
    @PatchMapping("/trips/{id}/price")
    public ResponseEntity<GiftTripInquiryResponse> updateTripPrice(
            @PathVariable Long id,
            @RequestParam BigDecimal value) {
        return ResponseEntity.ok(tripService.updatePrice(id, value));
    }

    /**
     * POST /api/admin/gifts/trips/{id}/create-private-date
     * Kreira privatni booking termin iz gift trip upita.
     * Identičan flow kao za /api/admin/inquiries/{id}/create-private-date.
     */
    @PostMapping("/trips/{id}/create-private-date")
    public ResponseEntity<AdminDateResponse> createPrivateDateFromGiftTrip(
            @PathVariable Long id,
            @RequestBody CreatePrivateDateRequest request) {
        return ResponseEntity.ok(tripService.createPrivateDateFromGiftTrip(id, request));
    }
}
