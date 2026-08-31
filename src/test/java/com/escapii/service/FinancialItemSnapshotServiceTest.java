package com.escapii.service;

import com.escapii.dto.PricePreviewResponse;
import com.escapii.model.AllocationType;
import com.escapii.model.Booking;
import com.escapii.model.BookingFinancialItem;
import com.escapii.model.ItemType;
import com.escapii.service.impl.FinancialItemSnapshotService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Snapshot mora da napravi tacno stavke koje booking stvarno ima - bez fantomskih
 * unosa za dodatke koji su ostali null. Zbir customerTotal-a mora se poklopiti
 * sa totalEurAll iz price previewa (osnov za obracun sa agencijom).
 */
class FinancialItemSnapshotServiceTest {

    private final FinancialItemSnapshotService svc = new FinancialItemSnapshotService();

    private Booking emptyBooking() {
        Booking b = new Booking();
        b.setFinancialItems(new ArrayList<>());
        return b;
    }

    @Test
    void snapshot_pravi_samo_stavke_koje_bookingImaAktivne() {
        PricePreviewResponse p = PricePreviewResponse.builder()
                .basePricePerPerson(300).accommodationExtraPerPerson(0)
                .breakfastPerPerson(0).seatsTogether(0).insurancePerPerson(0)
                .eurPerPerson(300).exclusionCostFlat(0).soloSurcharge(0)
                .cabinSuitcaseCount(0).cabinSuitcaseTotal(0).revealBoxTotal(0)
                .totalEurAll(600).exclusionCount(0).numberOfTravelers(2).numberOfNights(3)
                .build();

        Booking b = emptyBooking();
        svc.snapshot(b, p);

        assertEquals(1, b.getFinancialItems().size(), "samo BASE_PACKAGE - ostale stavke nisu aktivne");
        BookingFinancialItem base = b.getFinancialItems().get(0);
        assertEquals(ItemType.BASE_PACKAGE, base.getItemType());
        assertEquals(AllocationType.MARGIN_50_50, base.getAllocationType());
        assertEquals(new BigDecimal("600.00"), base.getCustomerTotal());
        assertNull(base.getAgencyCost(), "agencyCost mora ostati null - admin ce ga uneti kasnije");
    }

    @Test
    void snapshot_kompletan_primer_sabira_sve_stavke_na_totalEurAll() {
        // Primer iz akceptanse: base 359, exclusions 20, reveal box 35 = ukupno 414
        PricePreviewResponse p = PricePreviewResponse.builder()
                .basePricePerPerson(179).accommodationExtraPerPerson(0)
                .breakfastPerPerson(0).seatsTogether(0).insurancePerPerson(0)
                .eurPerPerson(179).exclusionCostFlat(20).soloSurcharge(0)
                .cabinSuitcaseCount(0).cabinSuitcaseTotal(0).revealBoxTotal(35)
                .totalEurAll(413).exclusionCount(2).numberOfTravelers(2).numberOfNights(3)
                .build();
        // 179 × 2 = 358; + 20 (excl) + 35 (box) = 413

        Booking b = emptyBooking();
        svc.snapshot(b, p);

        assertEquals(3, b.getFinancialItems().size());
        BigDecimal sum = b.getFinancialItems().stream()
                .map(BookingFinancialItem::getCustomerTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("413.00"), sum,
                "zbir stavki mora biti jednak totalEurAll (invariant za AgencySettlementCalculator)");
    }

    /**
     * Breakfast: unit mora biti 20€/os/noc, quantity = putnici × noci - inace
     * admin breakdown izgleda kao "20 × 2 = 40" umesto "20 × (2 × 3) = 120".
     */
    @Test
    void snapshot_breakfast_prikazuje_putnike_puta_noci() {
        // 20€/os/noc × 2 osobe × 3 noci = 120€; PricePreview daje breakfastPerPerson = 60 (20 × 3)
        PricePreviewResponse p = PricePreviewResponse.builder()
                .basePricePerPerson(300).accommodationExtraPerPerson(0)
                .breakfastPerPerson(60).seatsTogether(0).insurancePerPerson(0)
                .eurPerPerson(360).exclusionCostFlat(0).soloSurcharge(0)
                .cabinSuitcaseCount(0).cabinSuitcaseTotal(0).revealBoxTotal(0)
                .totalEurAll(720).exclusionCount(0).numberOfTravelers(2).numberOfNights(3)
                .build();

        Booking b = emptyBooking();
        svc.snapshot(b, p);

        BookingFinancialItem br = b.getFinancialItems().stream()
                .filter(i -> i.getItemType() == ItemType.BREAKFAST)
                .findFirst().orElseThrow();
        assertEquals(6, br.getQuantity(), "quantity = 2 putnika × 3 noci");
        assertEquals(new BigDecimal("20.00"), br.getUnitCustomerPrice(),
                "unit = 20€/os/noc, ne 60€ (jer bi bio dupli racun)");
        assertEquals(new BigDecimal("120.00"), br.getCustomerTotal(),
                "total = 2 × 3 × 20 = 120");
        assertTrue(br.getDescription().contains("2 putnika"),
                "opis mora spomenuti broj putnika");
        assertTrue(br.getDescription().contains("3 noći"),
                "opis mora spomenuti broj noci");
    }

    /**
     * Isključivanja: prvo je besplatno (BEG aerodrom pravilo), pa quantity mora
     * biti SAMO naplativa isk. × putnici, ne ukupna. Unit = 10€/os/isk.
     */
    @Test
    void snapshot_exclusion_prikazuje_samo_naplativa() {
        // 3 isk. ukupno (1 besplatno + 2 naplativa) × 2 putnika × 10€ = 40€
        PricePreviewResponse p = PricePreviewResponse.builder()
                .basePricePerPerson(300).accommodationExtraPerPerson(0)
                .breakfastPerPerson(0).seatsTogether(0).insurancePerPerson(0)
                .eurPerPerson(300).exclusionCostFlat(40).soloSurcharge(0)
                .cabinSuitcaseCount(0).cabinSuitcaseTotal(0).revealBoxTotal(0)
                .totalEurAll(640).exclusionCount(3).numberOfTravelers(2).numberOfNights(3)
                .build();

        Booking b = emptyBooking();
        svc.snapshot(b, p);

        BookingFinancialItem ex = b.getFinancialItems().stream()
                .filter(i -> i.getItemType() == ItemType.DESTINATION_EXCLUSIONS)
                .findFirst().orElseThrow();
        // billable = exclFlat / (10 × n) = 40 / 20 = 2 → quantity = 2 × 2 putnika = 4
        assertEquals(4, ex.getQuantity(),
                "quantity mora biti samo naplativa × putnici (2 naplativa × 2 = 4), ne ukupno 6");
        assertEquals(new BigDecimal("10.00"), ex.getUnitCustomerPrice(),
                "unit = 10€/os/isk (deterministicna vrednost, ne izvedena deljenjem exclFlat/exclCount)");
        assertEquals(new BigDecimal("40.00"), ex.getCustomerTotal());
        assertTrue(ex.getDescription().contains("2 naplativa"));
        assertTrue(ex.getDescription().contains("3"), "opis mora spomenuti ukupno 3 isk.");
    }

    @Test
    void snapshot_soloDoplata_kao_zasebna_stavka() {
        PricePreviewResponse p = PricePreviewResponse.builder()
                .basePricePerPerson(300).accommodationExtraPerPerson(0)
                .breakfastPerPerson(0).seatsTogether(0).insurancePerPerson(0)
                .eurPerPerson(300).exclusionCostFlat(0).soloSurcharge(60)
                .cabinSuitcaseCount(0).cabinSuitcaseTotal(0).revealBoxTotal(0)
                .totalEurAll(360).exclusionCount(0).numberOfTravelers(1).numberOfNights(3)
                .build();

        Booking b = emptyBooking();
        svc.snapshot(b, p);

        BookingFinancialItem solo = b.getFinancialItems().stream()
                .filter(i -> i.getItemType() == ItemType.SOLO_SURCHARGE)
                .findFirst().orElseThrow();
        assertEquals(AllocationType.ESCAPII_100, solo.getAllocationType());
        assertEquals(new BigDecimal("60.00"), solo.getCustomerTotal());
    }
}
