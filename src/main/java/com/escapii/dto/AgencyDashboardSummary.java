package com.escapii.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Agregat za tab "Obracun i fakturisanje agencija". Razdvaja tri razlicita
 * pogleda umesto jedne "Escapii zaradio" sume koja bi mesala projekciju i
 * stvarno naplacen novac:
 *
 * <ul>
 *   <li><b>projected</b> - svi CONFIRMED bookinzi sa readyForInvoice=true
 *       (bez PENDING/CANCELLED, bez nepopunjenih troskova); pokazuje sta
 *       Escapii MOZE zaraditi kad se sve zavrsi</li>
 *   <li><b>invoiced</b> - samo INVOICED (racuni izdati agenciji, jos neplaceni)</li>
 *   <li><b>paid</b> - samo PAID (novac koji je stvarno stigao)</li>
 * </ul>
 *
 * VOIDED se ne racuna u nijedan zbir (bilo pa nije).
 * Svi bookinzi u svim statusima se broje kroz {@code count*} polja.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgencyDashboardSummary {

    /** Escapii deo za CONFIRMED bookinge koji su ready ali jos nefakturisani + fakturisani + placeni. */
    private BigDecimal projectedEscapiiTotal;
    private Integer projectedCount;

    /** Escapii deo za INVOICED bookinge (racuni koji cekaju uplatu). */
    private BigDecimal invoicedEscapiiTotal;
    private Integer invoicedCount;

    /** Escapii deo za PAID bookinge (stvarno naplaceno). */
    private BigDecimal paidEscapiiTotal;
    private Integer paidCount;

    /** Brojaci po statusima (uvid, ne finansijski). */
    private Integer needsCostsCount;
    private Integer readyForInvoiceCount;
    private Integer voidedCount;
}
