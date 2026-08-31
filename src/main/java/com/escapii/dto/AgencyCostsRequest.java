package com.escapii.dto;

import com.escapii.model.ItemType;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;

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
 *
 * <p><b>Brisanje troskova:</b> {@code null} u polju znaci "ne menjaj postojecu
 * vrednost" (idempotencija). Da bi admin obrisao pogresno unet trosak i vratio
 * stavku u status MISSING_COST, ItemType te stavke se dodaje u {@link #clear}.
 * Za BASE_PACKAGE, dodavanje u clear brise i flight i hotel istovremeno.
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
    // Napomena: ACCOMMODATION_UPGRADE nema unos - upgrade je fiksan fee/osoba
    // koji predstavlja cistu 50/50 zaradu bez troska agencije (snapshot ga
    // odmah postavi na 0). Videti FinancialItemSnapshotService.
    private BigDecimal breakfastAgencyCost;
    private BigDecimal seatsTogetherAgencyCost;
    private BigDecimal cabinSuitcaseAgencyCost;
    private BigDecimal insuranceAgencyCost;

    /** ItemType-ovi cije troskove admin zeli da OBRISE (vrati u null). Opciono. */
    private Set<ItemType> clear;
}
