package com.escapii.service;

import com.escapii.dto.GiftVoucherRequest;
import com.escapii.dto.GiftVoucherResponse;
import com.escapii.dto.GiftVoucherRevealResponse;
import com.escapii.dto.GiftVoucherValidateRequest;
import com.escapii.dto.GiftVoucherValidateResponse;

import java.util.List;

public interface GiftVoucherService {

    /** Kreira novi vaučer upit (PENDING). Šalje notifikaciju timu. */
    GiftVoucherResponse createVoucher(GiftVoucherRequest request);

    /**
     * Validira vaučer kod — javni endpoint.
     * Vraća amount ako je ACTIVE i nije istekao, inače uniformnu grešku.
     * Nikad ne otkriva razlog neuspešne validacije.
     */
    GiftVoucherValidateResponse validate(GiftVoucherValidateRequest request);

    /**
     * Reveal za primaoca — vraća detalje vaučera ako je ACTIVE.
     * Javni endpoint, rate limited. Vraća ime davaoca i poruku ali NE email.
     */
    GiftVoucherRevealResponse reveal(String code);

    /** Admin: lista svih vaučera, sortirano po datumu. */
    List<GiftVoucherResponse> getAllVouchers();

    /**
     * Admin: aktivira vaučer (PENDING → ACTIVE) i šalje email primaocu sa kodom.
     * Postavlja activatedAt i expiresAt (1 godina od aktivacije).
     */
    GiftVoucherResponse activateVoucher(Long id);

    /**
     * Admin: markira vaučer kao iskorišćen (ACTIVE → USED).
     * Poziva se kada admin potvrdi booking u kome je primenjen vaučer.
     */
    GiftVoucherResponse markUsed(Long id, Long bookingRef);
}
