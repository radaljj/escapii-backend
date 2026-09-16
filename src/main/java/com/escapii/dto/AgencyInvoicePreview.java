package com.escapii.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Šta bi ušlo u zbirnu fakturu agenciji da se klikne „Fakturiši" sada. Popup u panelu
 * prikazuje tačno ovo pre nego što admin potvrdi.
 *
 * @param included     završena putovanja koja ulaze, sa Escapii zaradom po svakom
 * @param needsCosts   završena putovanja koja NE ulaze (nisu uneti troškovi) - ulaze u sledeću
 * @param inProgress   potvrđena putovanja koja još traju - ulaze posle povratka
 * @param periodFrom   najraniji polazak obuhvaćenih rezervacija (period fakture od)
 * @param periodTo     najkasniji povratak obuhvaćenih rezervacija (period fakture do)
 * @param periodFrom   najraniji polazak obuhvaćenih rezervacija (period fakture od)
 * @param periodTo     najkasniji povratak obuhvaćenih rezervacija (period fakture do)
 * @param canInvoice   false = dugme se ne nudi; {@code blocker} kaže zašto
 */
public record AgencyInvoicePreview(
        Long agencyId,
        String agencyName,
        String agencyEmail,
        BigDecimal amount,
        int bookingCount,
        List<Line> included,
        List<Skipped> needsCosts,
        int inProgress,
        LocalDate periodFrom,
        LocalDate periodTo,
        String suggestedDescription,
        boolean canInvoice,
        String blocker
) {
    public record Line(Long bookingId, String bookingRef, LocalDate departureDate, LocalDate returnDate,
                       Integer travelers, BigDecimal escapiiEarnings) {}

    public record Skipped(Long bookingId, String bookingRef, LocalDate returnDate, String reason) {}
}
