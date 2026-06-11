package com.escapii.controller;

import com.escapii.dto.GiftVoucherResponse;
import com.escapii.service.GiftVoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin endpointi za upravljanje gift vaučerima.
 * Zaštićeni X-Admin-Key headerom (AdminKeyFilter).
 *
 * GET    /api/admin/gifts/vouchers
 * PATCH  /api/admin/gifts/vouchers/{id}/activate   - PENDING → ACTIVE, šalje email primaocu
 * PATCH  /api/admin/gifts/vouchers/{id}/mark-used  - ACTIVE → USED
 */
@RestController
@RequestMapping("/api/admin/gifts")
@RequiredArgsConstructor
public class GiftAdminController {

    private final GiftVoucherService voucherService;

    /** GET /api/admin/gifts/vouchers - svi vaučeri, sortirano po datumu kreiranja desc. */
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

    /**
     * PATCH /api/admin/gifts/vouchers/{id}/reactivate
     * Vraća vaučer u ACTIVE stanje (RESERVED/USED/EXPIRED → ACTIVE).
     * Korisno kada vaučer ostane zarobljen zbog otkazane/obrisane rezervacije
     * koja nije prošla kroz normalni CANCELLED flow.
     */
    @PatchMapping("/vouchers/{id}/reactivate")
    public ResponseEntity<GiftVoucherResponse> reactivateVoucher(@PathVariable Long id) {
        return ResponseEntity.ok(voucherService.reactivateVoucher(id));
    }
}
