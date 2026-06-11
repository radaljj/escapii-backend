package com.escapii.dto;

import java.math.BigDecimal;

/**
 * Odgovor na validaciju vaučer koda.
 *
 * Ako je validan: valid=true, amount=iznos (uvek sa servera, nikad sa klijenta).
 * Ako nije validan: valid=false, amount=null, message=uniformna poruka greške.
 *
 * SIGURNOST: Ista poruka greške za sve slučajeve (ne postoji, PENDING, USED, EXPIRED)
 * - ne otkriva informacije o stanju koda napadaču.
 */
public record GiftVoucherValidateResponse(
        boolean valid,
        BigDecimal amount,
        String message
) {
    public static GiftVoucherValidateResponse ok(BigDecimal amount) {
        return new GiftVoucherValidateResponse(true, amount, null);
    }

    public static GiftVoucherValidateResponse invalid() {
        return new GiftVoucherValidateResponse(false, null,
                "Vaučer kod nije validan ili nije aktivan.");
    }
}
