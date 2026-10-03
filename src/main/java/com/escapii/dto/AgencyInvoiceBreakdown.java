package com.escapii.dto;

import com.escapii.model.AllocationType;
import com.escapii.model.ItemType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Obrazloženje zbirne fakture agenciji po rezervaciji i po stavci - panel ga nudi kao tekst
 * za kopiranje u mejl, jer na samoj fakturi piše samo „Marketinške usluge". Radi i za
 * pregled (šta bi ušlo sada, {@code invoiceNumber} = null) i za već izdatu fakturu.
 *
 * <p>Sve iznose daje {@code AgencySettlementCalculator} - ovde se samo mapiraju. EUR, 2 decimale.
 *
 * @param invoiceNumber broj fakture, null za pregled (još nije izdata)
 * @param amount        iznos fakture = zbir {@code escapiiEarnings} po rezervacijama
 */
public record AgencyInvoiceBreakdown(
        String invoiceNumber,
        String agencyName,
        LocalDate periodFrom,
        LocalDate periodTo,
        BigDecimal amount,
        int bookingCount,
        List<BookingBreakdown> bookings
) {
    /**
     * Jedna rezervacija: koliko je kupac platio, koliko su troškovi agencije, kako je marža
     * podeljena i koliko od svega ide Escapii-ju ({@code escapiiEarnings} = deo na fakturi).
     *
     * @param destination dodeljena destinacija, može biti null (nije još dodeljena)
     */
    public record BookingBreakdown(
            Long bookingId,
            String bookingRef,
            LocalDate departureDate,
            LocalDate returnDate,
            Integer travelers,
            String destination,
            BigDecimal grossBookingValue,
            BigDecimal customerCashAmount,
            BigDecimal voucherAmount,
            BigDecimal agencyCostsTotal,
            BigDecimal sharedMarginTotal,
            BigDecimal escapiiSharedMarginPart,
            BigDecimal agencyMarginPart,
            BigDecimal escapiiExclusiveRevenue,
            BigDecimal escapiiEarnings,
            List<Item> items
    ) {}

    /**
     * Stavka rezervacije.
     *
     * @param customerTotal šta je kupac platio za stavku
     * @param agencyCost    unet trošak agencije; null kad nije unet (samo 50/50 stavke) ili kad nema smisla (ESCAPII_100)
     * @param margin        customerTotal - agencyCost za 50/50 stavke; null inače
     * @param escapiiPart   deo customerTotal koji pripada Escapii-ju (50/50: pola marže; ESCAPII_100: cela stavka)
     * @param agencyPart    deo customerTotal koji pripada agenciji (50/50: trošak + pola marže; AGENCY_100: cela stavka)
     */
    public record Item(
            ItemType itemType,
            AllocationType allocationType,
            String description,
            Integer quantity,
            BigDecimal customerTotal,
            BigDecimal agencyCost,
            BigDecimal margin,
            BigDecimal escapiiPart,
            BigDecimal agencyPart
    ) {}
}
