package com.escapii.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * Per-stavku unos agencijskih troskova. Sva polja su opciona - salju se samo
 * ona koja odgovaraju stvarnim stavkama na bookingu. Nepostojeci ItemType u
 * financialItems-ima se ignorise (nema smisla setovati trosak za doručak ako
 * ga booking uopste nema).
 *
 * <p>Za BASE_PACKAGE, sistem cita {@code flightAgencyCost} + {@code hotelAgencyCost}
 * i njihov zbir postavlja kao ukupni {@code agencyCost} stavke. Ako su samo
 * flight/hotel poslati, koriste se; ako je poslat i {@code baseAgencyCost}, mora
 * biti jednak zbiru inace endpoint odbija.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgencyCostsRequest {

    // ── BASE_PACKAGE (podeljen na dva dela) ─────────────────────────
    private BigDecimal flightAgencyCost;
    private BigDecimal hotelAgencyCost;

    // ── Ostali zajednicki dodaci (MARGIN_50_50) ────────────────────
    private BigDecimal accommodationUpgradeAgencyCost;
    private BigDecimal breakfastAgencyCost;
    private BigDecimal seatsTogetherAgencyCost;
    private BigDecimal cabinSuitcaseAgencyCost;
    private BigDecimal insuranceAgencyCost;
}
