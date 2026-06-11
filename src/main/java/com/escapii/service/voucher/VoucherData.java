package com.escapii.service.voucher;

import java.time.LocalDate;

/**
 * Svi dinamički podaci potrebni za PDF poklon vaučera.
 *
 * @param amount          Vrednost vaučera u evrima (npr. 500)
 * @param voucherCode     Jedinstveni kod (npr. "ESC-A3KM-P2HT-X9QR")
 * @param issuedAt        Datum izdavanja (aktivacije)
 * @param expiresAt       Datum isteka (issuedAt + 12 meseci)
 * @param buyerName       Ime kupca - ispisuje se na vaučeru
 * @param personalMessage Lična poruka kupca (može biti null/prazno - blok se sakriva)
 */
public record VoucherData(
    int amount,
    String voucherCode,
    LocalDate issuedAt,
    LocalDate expiresAt,
    String buyerName,
    String personalMessage
) {
    /**
     * Factory metoda: expiresAt se automatski izračunava kao issuedAt + 12 meseci.
     */
    public static VoucherData of(
        int amount,
        String voucherCode,
        LocalDate issuedAt,
        String buyerName,
        String personalMessage
    ) {
        return new VoucherData(
            amount, voucherCode,
            issuedAt, issuedAt.plusMonths(12),
            buyerName, personalMessage
        );
    }
}
