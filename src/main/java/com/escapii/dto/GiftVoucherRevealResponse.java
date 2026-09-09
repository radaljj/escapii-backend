package com.escapii.dto;

import com.escapii.model.GiftVoucher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Javni reveal response - vraća se korisniku koji unese kod na /poklon stranici.
 *
 * <p>Jedan odgovor za obe vrste poklona, jer je i ulaz jedan (kod sa vaučera):
 * {@code kind} kaže šta je stiglo. {@code VOUCHER} nosi iznos i poruku kao i do
 * sada; {@code TRIP} nosi termin, aerodrom i putnike u {@code trip}, a
 * {@code amount} i {@code expiresAt} su null - poklonjeno putovanje nema cenu
 * za obdarenog ni rok važenja.
 */
public record GiftVoucherRevealResponse(
        boolean valid,
        String kind,
        BigDecimal amount,
        String buyerName,
        String giftMessage,
        LocalDateTime activatedAt,
        LocalDateTime expiresAt,
        String message,
        TripDetails trip
) {
    public static final String KIND_VOUCHER = "VOUCHER";
    public static final String KIND_TRIP    = "TRIP";

    /**
     * Ono što obdareni sme da vidi o poklonjenom putovanju. Namerno bez cene i
     * bez šifre rezervacije - šifru već ima, to je kod kojim je i došao.
     */
    public record TripDetails(
            LocalDate departureDate,
            LocalDate returnDate,
            int nights,
            int travelers,
            String airportCode,
            String airportCity,
            String airportName,
            List<String> passengers,
            String recipientName
    ) {}

    public static GiftVoucherRevealResponse ok(GiftVoucher v) {
        // Prikazujemo preostali saldo (amount - usedAmount) jer vaučer može biti delimično iskorišćen
        BigDecimal remaining = v.getAmount().subtract(v.getUsedAmount());
        return new GiftVoucherRevealResponse(
                true,
                KIND_VOUCHER,
                remaining,
                v.getBuyerName(),
                v.getGiftMessage(),
                v.getActivatedAt(),
                v.getExpiresAt(),
                null,
                null
        );
    }

    /**
     * Poklonjeno putovanje: {@code buyerName} je ko poklanja (null kad se ne prikazuje -
     * istu odluku donosi {@code TripVoucherData} i za PDF), sve o putu je u {@code trip}.
     */
    public static GiftVoucherRevealResponse trip(String buyerName, TripDetails trip) {
        return new GiftVoucherRevealResponse(
                true,
                KIND_TRIP,
                null,
                (buyerName == null || buyerName.isBlank()) ? null : buyerName,
                null,
                null,
                null,
                null,
                trip
        );
    }

    public static GiftVoucherRevealResponse invalid() {
        return new GiftVoucherRevealResponse(false, null, null, null, null, null, null,
                "Vaučer kod nije validan ili nije aktivan.", null);
    }
}
