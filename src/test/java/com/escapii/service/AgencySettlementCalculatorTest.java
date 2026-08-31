package com.escapii.service;

import com.escapii.dto.AgencySettlementResponse;
import com.escapii.dto.AgencySettlementResponse.LineStatus;
import com.escapii.dto.AgencySettlementResponse.WhoPaysWhom;
import com.escapii.model.Booking;
import com.escapii.model.BookingFinancialItem;
import com.escapii.model.ItemType;
import com.escapii.service.impl.AgencySettlementCalculatorImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kalkulator je jedini izvor svih finansijskih formula (marze 50/50, vaucer kao
 * unapred naplacen novac, klasifikacija stavki po AllocationType). Ovi testovi
 * cuvaju invarijante koje ne smeju da procure kroz zadnja vrata:
 *
 * <ul>
 *   <li>escapii + agency = bruto vrednost (nijedan cent ne curi)</li>
 *   <li>vaucer ne dira maržu, samo netto settlement (smer isplate)</li>
 *   <li>ESCAPII_100 stavke idu 100% Escapii-ju</li>
 *   <li>nedostajuci troskovi blokiraju readyForInvoice</li>
 *   <li>neparni cent ide agenciji (Escapii deo HALF_DOWN, agencija ostatak)</li>
 * </ul>
 */
class AgencySettlementCalculatorTest {

    private final AgencySettlementCalculator calc = new AgencySettlementCalculatorImpl();

    // ── Helperi za konstrukciju bookinga ────────────────────────────────

    private Booking booking(int totalPriceAll, int voucherDiscount) {
        Booking b = new Booking();
        b.setId(1L);
        b.setBookingRef("ESC-test0001");
        b.setTotalPriceAll(totalPriceAll);
        b.setVoucherDiscount(voucherDiscount);
        b.setAgencyIdSnapshot(7L);
        b.setAgencyNameSnapshot("Ipanema Travel");
        b.setFinancialItems(new ArrayList<>());
        // Default: CONFIRMED (kupac je platio) - jedini status u kome je
        // readyForInvoice moguce. Testovi koji zele PENDING/CANCELLED to
        // eksplicitno postave posle.
        b.setStatus(com.escapii.model.BookingStatus.CONFIRMED);
        return b;
    }

    private void addItem(Booking b, ItemType type, BigDecimal customerTotal, BigDecimal agencyCost) {
        BookingFinancialItem i = new BookingFinancialItem();
        i.setBooking(b);
        i.setItemType(type);
        i.setAllocationType(type.getAllocationType());
        i.setDescription(type.name());
        i.setQuantity(1);
        i.setUnitCustomerPrice(customerTotal);
        i.setCustomerTotal(customerTotal);
        i.setAgencyCost(agencyCost);
        b.getFinancialItems().add(i);
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
    private static BigDecimal bd(int v) { return new BigDecimal(v).setScale(2); }

    // ── 1. Osnovni primer iz akceptanse: 359 - 220 = 139 → Escapii 69.50 ─

    @Test
    void osnovniPrimer_marza50_50() {
        Booking b = booking(414, 0);
        addItem(b, ItemType.BASE_PACKAGE, bd(359), bd(220));
        addItem(b, ItemType.DESTINATION_EXCLUSIONS, bd(20), null);
        addItem(b, ItemType.REVEAL_BOX, bd(35), null);

        AgencySettlementResponse r = calc.calculate(b);

        assertEquals(bd(139), r.getSharedMarginTotal());
        assertEquals(bd("69.50"), r.getEscapiiSharedMarginPart());
        assertEquals(bd("69.50"), r.getAgencyMarginPart());
        assertEquals(bd(55), r.getEscapiiExclusiveRevenue());
        assertEquals(bd("124.50"), r.getEscapiiEarnings());
        assertEquals(bd("289.50"), r.getAgencyRetainedAmount());
        assertTrue(r.isReconciled());
        assertTrue(r.isReadyForInvoice());
    }

    // ── 2. Vise putnika ─────────────────────────────────────────────────

    @Test
    void viseputnika_marzaSePodeliJednako() {
        Booking b = booking(1000, 0);
        addItem(b, ItemType.BASE_PACKAGE, bd(1000), bd(600));

        AgencySettlementResponse r = calc.calculate(b);
        assertEquals(bd(400), r.getSharedMarginTotal());
        assertEquals(bd(200), r.getEscapiiSharedMarginPart());
        assertEquals(bd(200), r.getAgencyMarginPart());
    }

    // ── 3. Superior je 50/50 ────────────────────────────────────────────

    @Test
    void superior_marzaPodeljena50_50() {
        Booking b = booking(500, 0);
        addItem(b, ItemType.BASE_PACKAGE, bd(300), bd(180));
        addItem(b, ItemType.ACCOMMODATION_UPGRADE, bd(200), bd(120));

        AgencySettlementResponse r = calc.calculate(b);
        // (300-180) + (200-120) = 120 + 80 = 200 marza; Escapii 100
        assertEquals(bd(200), r.getSharedMarginTotal());
        assertEquals(bd(100), r.getEscapiiSharedMarginPart());
    }

    // ── 4. Doručak sa vise noci - jednak split ───────────────────────────

    @Test
    void dorucak_neDeliSePoNocima_samoPoMarzi() {
        Booking b = booking(200, 0);
        addItem(b, ItemType.BASE_PACKAGE, bd(120), bd(80));
        addItem(b, ItemType.BREAKFAST, bd(80), bd(40));

        AgencySettlementResponse r = calc.calculate(b);
        // 40 + 40 = 80 marza; Escapii 40
        assertEquals(bd(80), r.getSharedMarginTotal());
        assertEquals(bd(40), r.getEscapiiSharedMarginPart());
    }

    // ── 5. Sedista 50/50 ────────────────────────────────────────────────

    @Test
    void sedistaMarza_50_50() {
        Booking b = booking(100, 0);
        addItem(b, ItemType.BASE_PACKAGE, bd(52), bd(30));
        addItem(b, ItemType.SEATS_TOGETHER, bd(48), bd(24));

        AgencySettlementResponse r = calc.calculate(b);
        // 22 + 24 = 46 marza; Escapii 23
        assertEquals(bd(46), r.getSharedMarginTotal());
        assertEquals(bd(23), r.getEscapiiSharedMarginPart());
    }

    // ── 7. Placena iskljucivanja = 100% Escapii ──────────────────────────

    @Test
    void iskljucivanja_100_escapii() {
        Booking b = booking(30, 0);
        addItem(b, ItemType.BASE_PACKAGE, bd(10), bd(5));
        addItem(b, ItemType.DESTINATION_EXCLUSIONS, bd(20), null);

        AgencySettlementResponse r = calc.calculate(b);
        var exclLine = r.getLineItems().stream()
                .filter(l -> l.getItemType() == ItemType.DESTINATION_EXCLUSIONS)
                .findFirst().orElseThrow();
        assertEquals(bd(20), exclLine.getEscapiiShare());
        assertEquals(bd(0), exclLine.getAgencyShare());
        assertNull(exclLine.getMargin(), "ESCAPII_100 stavka nema pojam marze");
        assertEquals(LineStatus.OK, exclLine.getStatus(),
                "iskljucivanja ne blokiraju fakturu iako nemaju agencyCost");
    }

    // ── 8. Reveal Box 100% Escapii ──────────────────────────────────────

    @Test
    void revealBox_100_escapii() {
        Booking b = booking(35, 0);
        addItem(b, ItemType.REVEAL_BOX, bd(35), null);

        AgencySettlementResponse r = calc.calculate(b);
        assertEquals(bd(35), r.getEscapiiExclusiveRevenue());
        assertEquals(bd(35), r.getEscapiiEarnings());
        assertEquals(bd(0), r.getAgencyRetainedAmount());
    }

    // ── 9. Vaucer NE umanjuje osnovu za podelu, ali menja netto ─────────

    @Test
    void vaucer_neUticeNaMarzu_menjaSmer() {
        // Kupac je platio 314€ kesom + 100€ vaucer = bruto 414€
        Booking b = booking(314, 100);
        addItem(b, ItemType.BASE_PACKAGE, bd(359), bd(220));
        addItem(b, ItemType.DESTINATION_EXCLUSIONS, bd(20), null);
        addItem(b, ItemType.REVEAL_BOX, bd(35), null);

        AgencySettlementResponse r = calc.calculate(b);

        // Podela iste kao bez vaucera:
        assertEquals(bd("124.50"), r.getEscapiiEarnings());
        // Ali vaucer smanjuje sto agencija transferuje Escapii-ju:
        assertEquals(bd(100), r.getVoucherApplied());
        assertEquals(bd("24.50"), r.getNetSettlement());
        assertEquals(WhoPaysWhom.AGENCY_PAYS_ESCAPII, r.getWhoPaysWhom());
        // Bruto ostaje 414
        assertEquals(bd(414), r.getGrossBookingValue());
        assertEquals(bd(314), r.getCustomerCashAmount());
    }

    @Test
    void veliki_vaucer_okreceSmer_escapiiPlacaAgenciji() {
        // Vaucer 200€, escapii ce zaraditi samo 69.50 → duguje 130.50 agenciji
        Booking b = booking(159, 200);
        addItem(b, ItemType.BASE_PACKAGE, bd(359), bd(220));

        AgencySettlementResponse r = calc.calculate(b);
        assertEquals(bd("69.50"), r.getEscapiiEarnings());
        assertEquals(bd("-130.50"), r.getNetSettlement());
        assertEquals(WhoPaysWhom.ESCAPII_PAYS_AGENCY, r.getWhoPaysWhom());
    }

    // ── 10. Nedostajuci trosak blokira fakturu ──────────────────────────

    @Test
    void nedostajuciTrosak_blokiraFakturu() {
        Booking b = booking(359, 0);
        addItem(b, ItemType.BASE_PACKAGE, bd(359), null); // nema agencyCost

        AgencySettlementResponse r = calc.calculate(b);
        assertFalse(r.isReadyForInvoice());
        assertFalse(r.getValidationErrors().isEmpty());
        var baseLine = r.getLineItems().get(0);
        assertEquals(LineStatus.MISSING_COST, baseLine.getStatus());
    }

    // ── 11. Negativna marza je upozorenje ───────────────────────────────

    @Test
    void negativnaMarza_dajeUpozorenje() {
        Booking b = booking(100, 0);
        addItem(b, ItemType.BASE_PACKAGE, bd(100), bd(150)); // izgubili smo 50€

        AgencySettlementResponse r = calc.calculate(b);
        assertFalse(r.isReadyForInvoice());
        assertTrue(r.getValidationErrors().stream().anyMatch(e -> e.contains("Negativna")));
        var baseLine = r.getLineItems().get(0);
        assertEquals(LineStatus.NEGATIVE_MARGIN, baseLine.getStatus());
    }

    // ── 12. Invariant: Escapii + agencija = bruto ───────────────────────

    @Test
    void invariant_zbirStrana_uvekJednakBruto() {
        Booking b = booking(414, 0);
        addItem(b, ItemType.BASE_PACKAGE, bd(359), bd(220));
        addItem(b, ItemType.DESTINATION_EXCLUSIONS, bd(20), null);
        addItem(b, ItemType.REVEAL_BOX, bd(35), null);

        AgencySettlementResponse r = calc.calculate(b);
        BigDecimal sum = r.getEscapiiEarnings().add(r.getAgencyRetainedAmount());
        assertEquals(r.getGrossBookingValue(), sum, "escapii + agencija = bruto vrednost");
    }

    // ── 13. Neparni cent ide agenciji (139.01) ──────────────────────────

    @Test
    void neparni_centIdeAgenciji() {
        Booking b = booking(0, 0);
        addItem(b, ItemType.BASE_PACKAGE, bd("139.01"), bd(0));

        AgencySettlementResponse r = calc.calculate(b);
        assertEquals(bd("139.01"), r.getSharedMarginTotal());
        assertEquals(bd("69.50"), r.getEscapiiSharedMarginPart(), "HALF_DOWN → 69.50");
        assertEquals(bd("69.51"), r.getAgencyMarginPart(), "ostatak → 69.51 (agencija dobija cent)");
        // Invariant i dalje vazi
        assertEquals(bd("139.01"),
                r.getEscapiiSharedMarginPart().add(r.getAgencyMarginPart()));
    }

    // ── 14. WhoPaysWhom: bez vaucera - agencija placa ───────────────────

    @Test
    void bezVaucera_agencijaPlacaEscapii() {
        Booking b = booking(200, 0);
        addItem(b, ItemType.BASE_PACKAGE, bd(200), bd(100));

        AgencySettlementResponse r = calc.calculate(b);
        assertEquals(WhoPaysWhom.AGENCY_PAYS_ESCAPII, r.getWhoPaysWhom());
        assertTrue(r.getNetSettlement().signum() > 0);
    }

    @Test
    void vaucerJednakZaradi_nemaTransfera() {
        Booking b = booking(0, 50);
        addItem(b, ItemType.BASE_PACKAGE, bd(50), bd(0));

        AgencySettlementResponse r = calc.calculate(b);
        // marza 50 → escapii 25; agencija 25; escapii vec drzi 50 vaucer → duguje 25 agenciji
        assertEquals(bd(25), r.getEscapiiEarnings());
        assertEquals(bd("-25.00"), r.getNetSettlement());
        assertEquals(WhoPaysWhom.ESCAPII_PAYS_AGENCY, r.getWhoPaysWhom());
    }

    // ── 15. Empty items → nije spreman ──────────────────────────────────

    @Test
    void prazneStavke_nisuSpremneZaFakturu() {
        Booking b = booking(0, 0);
        AgencySettlementResponse r = calc.calculate(b);
        assertFalse(r.isReadyForInvoice());
    }

    // ── 16. Reconciliation mismatch ─────────────────────────────────────

    @Test
    void zbirStavki_MoraBitiJednakBruto() {
        Booking b = booking(500, 0); // bruto = 500
        addItem(b, ItemType.BASE_PACKAGE, bd(300), bd(100)); // stavke = 300, ne 500

        AgencySettlementResponse r = calc.calculate(b);
        assertFalse(r.isReconciled());
        assertTrue(r.getValidationErrors().stream().anyMatch(e -> e.contains("poklapa")));
    }

    // ── 18. Solo doplata = 100% Escapii ─────────────────────────────────

    @Test
    void soloDoplata_100_escapii() {
        Booking b = booking(60, 0);
        addItem(b, ItemType.SOLO_SURCHARGE, bd(60), null);

        AgencySettlementResponse r = calc.calculate(b);
        assertEquals(bd(60), r.getEscapiiExclusiveRevenue());
        assertEquals(bd(0), r.getAgencyRetainedAmount());
    }

    // ── 19. Kabinski kofer = 50/50 (potvrda tipa) ───────────────────────

    @Test
    void kofer_50_50() {
        Booking b = booking(100, 0);
        addItem(b, ItemType.CABIN_SUITCASE, bd(100), bd(60));
        AgencySettlementResponse r = calc.calculate(b);
        assertEquals(bd(40), r.getSharedMarginTotal());
        assertEquals(bd(20), r.getEscapiiSharedMarginPart());
    }

    // ── 20. PENDING/CANCELLED ne moze biti readyForInvoice ──────────────

    /**
     * Kupac jos nije platio (PENDING) - Escapii ne sme fakturisati agenciji
     * jer je novac jos rizican. Ceo obracun mora reci "nije ready" cak i ako
     * su svi troskovi popunjeni.
     */
    @Test
    void pending_ne_moze_biti_ready_iako_su_troskovi_popunjeni() {
        Booking b = booking(359, 0);
        b.setStatus(com.escapii.model.BookingStatus.PENDING);
        addItem(b, ItemType.BASE_PACKAGE, bd(359), bd(220));

        AgencySettlementResponse r = calc.calculate(b);
        assertFalse(r.isReadyForInvoice(),
                "PENDING booking ne sme biti ready za fakturu");
        assertTrue(r.getValidationErrors().stream().anyMatch(e -> e.contains("CONFIRMED")),
                "validation error mora eksplicitno reci sto");
    }

    // ── 21. Response nosi flight/hotel i broj fakture (za frontend modal) ──

    @Test
    void response_ima_flight_i_hotel_polje_za_BASE_PACKAGE() {
        Booking b = booking(359, 0);
        BookingFinancialItem base = new BookingFinancialItem();
        base.setBooking(b);
        base.setItemType(ItemType.BASE_PACKAGE);
        base.setAllocationType(ItemType.BASE_PACKAGE.getAllocationType());
        base.setQuantity(1);
        base.setUnitCustomerPrice(bd(359));
        base.setCustomerTotal(bd(359));
        base.setAgencyCost(bd(220));
        base.setFlightAgencyCost(new BigDecimal("150.00"));
        base.setHotelAgencyCost(new BigDecimal("70.00"));
        b.getFinancialItems().add(base);

        AgencySettlementResponse r = calc.calculate(b);
        var line = r.getLineItems().get(0);
        assertEquals(new BigDecimal("150.00"), line.getFlightAgencyCost(),
                "flight mora biti u response - modal ga prepopunjava pri drugom otvaranju");
        assertEquals(new BigDecimal("70.00"), line.getHotelAgencyCost());
    }

    @Test
    void response_ima_broj_fakture_i_datume() {
        Booking b = booking(100, 0);
        b.setAgencyInvoiceNumber("ESC-AG-2026-0007");
        b.setAgencyInvoicedAt(java.time.LocalDateTime.of(2026, 1, 15, 10, 0));
        b.setSettlementStatus(com.escapii.model.SettlementStatus.INVOICED);
        addItem(b, ItemType.BASE_PACKAGE, bd(100), bd(60));

        AgencySettlementResponse r = calc.calculate(b);
        assertEquals("ESC-AG-2026-0007", r.getAgencyInvoiceNumber(),
                "broj fakture mora biti u response - frontend ne treba drugi API poziv");
        assertEquals(java.time.LocalDateTime.of(2026, 1, 15, 10, 0), r.getAgencyInvoicedAt());
    }

    @Test
    void cancelled_takodje_nije_ready() {
        Booking b = booking(100, 0);
        b.setStatus(com.escapii.model.BookingStatus.CANCELLED);
        addItem(b, ItemType.BASE_PACKAGE, bd(100), bd(60));

        AgencySettlementResponse r = calc.calculate(b);
        assertFalse(r.isReadyForInvoice());
    }
}
