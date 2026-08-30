package com.escapii.dto;

import com.escapii.model.SettlementStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Jedan red u admin dashboardu "Obracun i fakturisanje agencija".
 * Predstavlja jednu rezervaciju (per-booking faktura) sa svim relevantnim
 * finansijskim iznosima izracunatim kroz {@code AgencySettlementCalculator}.
 *
 * <p>Sortirano po datumu polaska descending u endpoint-u; admin filtrira po
 * agenciji, periodu i settlement statusu.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgencyDashboardRow {

    private Long bookingId;
    private String bookingRef;
    private Long agencyId;
    private String agencyName;

    private LocalDate departureDate;
    private LocalDate returnDate;
    private String customerName;
    private Integer numberOfTravelers;

    private SettlementStatus settlementStatus;
    private String agencyInvoiceNumber;
    private LocalDateTime agencyInvoicedAt;
    private LocalDateTime agencyPaidAt;

    // Finansijski agregati (iz kalkulatora)
    private BigDecimal grossBookingValue;
    private BigDecimal voucherAmount;
    private BigDecimal escapiiEarnings;
    private BigDecimal netSettlement;
    private BigDecimal agencyRetainedAmount;

    /** True ako je za rezervaciju sve popunjeno da se moze finalizovati faktura. */
    private boolean readyForInvoice;
}
