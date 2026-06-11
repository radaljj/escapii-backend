package com.escapii.controller;

import com.escapii.dto.GiftVoucherRequest;
import com.escapii.dto.GiftVoucherResponse;
import com.escapii.dto.GiftVoucherRevealResponse;
import com.escapii.dto.GiftVoucherValidateRequest;
import com.escapii.dto.GiftVoucherValidateResponse;
import com.escapii.service.GiftVoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Javni endpointi za gift vaučer sistem.
 *
 * POST /api/gifts/vouchers         - kreiranje vaučer upita (rate limited: 3/sat)
 * POST /api/gifts/vouchers/validate - validacija koda (rate limited: 5/15min)
 *
 * SIGURNOST:
 * - Kreiranje vraća response BEZ koda - kod ide isključivo emailom primaocu nakon aktivacije
 * - Validacija vraća samo amount ako je validan, uniformna greška ako nije
 * - Admin endpointi (lista, aktivacija, mark-used) su u GiftAdminController-u (X-Admin-Key)
 */
@RestController
@RequestMapping("/api/gifts")
@RequiredArgsConstructor
public class GiftVoucherController {

    private final GiftVoucherService voucherService;

    /**
     * POST /api/gifts/vouchers
     * Korisnik šalje upit za gift vaučer. Admin dobija notifikaciju i ručno šalje instrukcije za uplatu.
     * Rate limited: max 3 zahteva po IP na sat (RateLimitingFilter).
     */
    @PostMapping("/vouchers")
    public ResponseEntity<GiftVoucherResponse> createVoucher(
            @Valid @RequestBody GiftVoucherRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(voucherService.createVoucher(request));
    }

    /**
     * POST /api/gifts/vouchers/validate
     * Proverava da li je vaučer kod validan i vraća iznos.
     * Koristi se u booking formi pre submita.
     * Rate limited: max 5 zahteva po IP na 15 minuta (RateLimitingFilter).
     */
    @PostMapping("/vouchers/validate")
    public ResponseEntity<GiftVoucherValidateResponse> validateVoucher(
            @Valid @RequestBody GiftVoucherValidateRequest request) {
        return ResponseEntity.ok(voucherService.validate(request));
    }

    /**
     * GET /api/gifts/vouchers/reveal?code=ESC-XXXX-XXXX-XXXX
     * Reveal endpoint za primaoca na /poklon stranici.
     * Vraća: iznos, ime davaoca, poruku - bez email adrese kupca.
     * Rate limited: isti limit kao validate (5/15min po IP).
     */
    @GetMapping("/vouchers/reveal")
    public ResponseEntity<GiftVoucherRevealResponse> revealVoucher(
            @RequestParam String code) {
        return ResponseEntity.ok(voucherService.reveal(code));
    }
}
