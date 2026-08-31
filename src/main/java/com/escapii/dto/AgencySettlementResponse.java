package com.escapii.dto;

import com.escapii.model.AllocationType;
import com.escapii.model.ItemType;
import com.escapii.model.SettlementStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Rezultat obracuna Escapii ↔ agencija za jednu rezervaciju.
 *
 * <p>Sve iznose racuna {@code AgencySettlementCalculator} - frontend/admin panel
 * samo prikazuje. Cene su u EUR sa 2 decimale.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgencySettlementResponse {

    private Long bookingId;
    private String bookingRef;
    private Long agencyId;
    private String agencyName;
    private String currency;
    private SettlementStatus settlementStatus;

    // ── Faktura (INVOICED/PAID/VOIDED) ─────────────────────────
    /** Broj fakture Escapii → agencija (ESC-AG-YYYY-NNNN). Null dok se ne finalizuje. */
    private String agencyInvoiceNumber;
    private LocalDateTime agencyInvoicedAt;
    private LocalDateTime agencyPaidAt;

    private List<LineItem> lineItems;

    // ── Kupac ───────────────────────────────────────────────────
    /** Bruto prodajna vrednost = ono sto se stvarno naplacuje u obracunu (kes + vaucer). */
    private BigDecimal grossBookingValue;
    /** Placeno kesom (totalPriceAll - vaucer). */
    private BigDecimal customerCashAmount;
    /** Placeno vaucerom (vaucerDiscount). */
    private BigDecimal voucherAmount;

    // ── Zajednicke stavke (MARGIN_50_50) ───────────────────────
    private BigDecimal sharedRevenueTotal;
    private BigDecimal agencyCostsTotal;
    private BigDecimal sharedMarginTotal;
    private BigDecimal escapiiSharedMarginPart;
    private BigDecimal agencyMarginPart;

    // ── Escapii-only stavke (ESCAPII_100) ──────────────────────
    private BigDecimal escapiiExclusiveRevenue;

    // ── Finalni iznosi ─────────────────────────────────────────
    /** Ukupno Escapii zaradjuje (marze + ekskluzivne stavke). Ne uzima vaucer u obzir. */
    private BigDecimal escapiiEarnings;
    /** Vaucer koji Escapii vec drzi kao unapred naplacen novac. */
    private BigDecimal voucherApplied;
    /** Neto transfer izmedju strana: escapiiEarnings - voucherApplied.
     *  Pozitivno = agencija placa Escapii; negativno = Escapii placa agenciji. */
    private BigDecimal netSettlement;
    private WhoPaysWhom whoPaysWhom;

    /** Ukupno agenciji ostaje: agencyCostsTotal + agencyMarginPart. */
    private BigDecimal agencyRetainedAmount;

    // ── Meta ──────────────────────────────────────────────────
    /** Zbir strana se poklapa sa bruto vrednosti (invariant). */
    private boolean reconciled;
    /** Sve 50/50 stavke imaju uneseni trosak, nema unclassified, nema negativne marze. */
    private boolean readyForInvoice;
    /** Lista upozorenja: nedostajuci trosak, negativna marza, unclassified stavka. */
    private List<String> validationErrors;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LineItem {
        private ItemType itemType;
        private AllocationType allocationType;
        private String description;
        private Integer quantity;
        private BigDecimal unitCustomerPrice;
        private BigDecimal customerTotal;
        /** Trosak agencije. Null = nije unet (samo za MARGIN_50_50); ne prikazuje se za ESCAPII_100. */
        private BigDecimal agencyCost;
        /** BASE_PACKAGE podunosi: avion i hotel odvojeno.
         *  Za sve ostale stavke oba su null. Frontend ih koristi da prepopuli modal. */
        private BigDecimal flightAgencyCost;
        private BigDecimal hotelAgencyCost;
        /** Marza stavke (customerTotal - agencyCost). Null ako trosak nije unet ili ESCAPII_100. */
        private BigDecimal margin;
        /** Escapii deo ove stavke. */
        private BigDecimal escapiiShare;
        /** Agencijski deo ove stavke (0 za ESCAPII_100). */
        private BigDecimal agencyShare;
        /** Status stavke za UI: OK, MISSING_COST, NEGATIVE_MARGIN. */
        private LineStatus status;
    }

    public enum LineStatus {
        OK,
        MISSING_COST,
        NEGATIVE_MARGIN
    }

    public enum WhoPaysWhom {
        AGENCY_PAYS_ESCAPII,
        ESCAPII_PAYS_AGENCY,
        NO_TRANSFER
    }
}
