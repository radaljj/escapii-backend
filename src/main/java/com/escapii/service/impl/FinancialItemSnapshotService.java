package com.escapii.service.impl;

import com.escapii.dto.PricePreviewResponse;
import com.escapii.model.AllocationType;
import com.escapii.model.Booking;
import com.escapii.model.BookingFinancialItem;
import com.escapii.model.ItemType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Snapshotuje sve prihodovne stavke rezervacije u {@link BookingFinancialItem}
 * u trenutku kreiranja bookinga. Iznosi ovde su ono sto je kupcu naplaceno
 * (pre eventualnog vaucera - vaucer se u obracunu tretira kao unapred naplacen
 * novac, ne kao popust na stavke).
 *
 * <p>Ne popunjava {@code agencyCost} - to ostaje null dok admin ne unese
 * troskove kroz panel. Escapii-only stavke (isključivanja, reveal box, solo
 * doplata) ne trebaju agencyCost i ostaju ok bez unosa.
 *
 * <p>Invariant: zbir svih {@code customerTotal} mora biti jednak
 * {@code totalPriceAll + voucherDiscount} (bruto vrednost rezervacije).
 * {@code AgencySettlementCalculator} to proverava.
 */
@Service
public class FinancialItemSnapshotService {

    /**
     * Kreira sve financial items za dati booking na osnovu preview izracuna.
     * Poziva se iz {@code BookingServiceImpl.createBooking} pre save-a, oslanja
     * se na JPA cascade da ih persistira zajedno sa Booking-om.
     */
    public void snapshot(Booking booking, PricePreviewResponse price) {
        int n = nz(price.getNumberOfTravelers());

        // 1. BASE_PACKAGE - uvek postoji. quantity = broj putnika.
        int base = nz(price.getBasePricePerPerson());
        addItem(booking, ItemType.BASE_PACKAGE,
                "Osnovni paket (let + hotel)",
                n, BigDecimal.valueOf(base), BigDecimal.valueOf((long) base * n));

        // 2. ACCOMMODATION_UPGRADE - samo ako je Superior/Premium izabran (extra > 0).
        int upgradePP = nz(price.getAccommodationExtraPerPerson());
        if (upgradePP > 0) {
            addItem(booking, ItemType.ACCOMMODATION_UPGRADE,
                    "Superior/Premium upgrade smestaja",
                    n, BigDecimal.valueOf(upgradePP), BigDecimal.valueOf((long) upgradePP * n));
        }

        // 3. BREAKFAST - iznos je vec ukupno po osobi za sve noci; quantity = putnici.
        int breakfastPP = nz(price.getBreakfastPerPerson());
        if (breakfastPP > 0) {
            addItem(booking, ItemType.BREAKFAST,
                    "Doručak u hotelu",
                    n, BigDecimal.valueOf(breakfastPP), BigDecimal.valueOf((long) breakfastPP * n));
        }

        // 4. SEATS_TOGETHER - fiksno po osobi za oba smera.
        int seatsPP = nz(price.getSeatsTogether());
        if (seatsPP > 0) {
            addItem(booking, ItemType.SEATS_TOGETHER,
                    "Sedista zajedno (oba smera)",
                    n, BigDecimal.valueOf(seatsPP), BigDecimal.valueOf((long) seatsPP * n));
        }

        // 5. CABIN_SUITCASE - selektivno po putniku, quantity = broj kofera.
        int cabinCount = nz(price.getCabinSuitcaseCount());
        int cabinTotal = nz(price.getCabinSuitcaseTotal());
        if (cabinCount > 0 && cabinTotal > 0) {
            BigDecimal unit = BigDecimal.valueOf((long) cabinTotal / cabinCount);
            addItem(booking, ItemType.CABIN_SUITCASE,
                    "Kabinski kofer",
                    cabinCount, unit, BigDecimal.valueOf(cabinTotal));
        }

        // 6. INSURANCE - dodatak je privremeno onemogucen (feature flag), ali logika
        //    ostaje za istorijske rezervacije koje su ga imale.
        int insurancePP = nz(price.getInsurancePerPerson());
        if (insurancePP > 0) {
            addItem(booking, ItemType.INSURANCE,
                    "Putno osiguranje",
                    n, BigDecimal.valueOf(insurancePP), BigDecimal.valueOf((long) insurancePP * n));
        }

        // 7. SOLO_SURCHARGE - 100% Escapii, iako po broju putnika = 1.
        int solo = nz(price.getSoloSurcharge());
        if (solo > 0) {
            addItem(booking, ItemType.SOLO_SURCHARGE,
                    "Doplata za solo putnika",
                    1, BigDecimal.valueOf(solo), BigDecimal.valueOf(solo));
        }

        // 8. DESTINATION_EXCLUSIONS - 100% Escapii, unit = 10€/os × broj naplativih.
        int exclFlat = nz(price.getExclusionCostFlat());
        int exclCount = nz(price.getExclusionCount());
        if (exclFlat > 0) {
            addItem(booking, ItemType.DESTINATION_EXCLUSIONS,
                    "Isključivanja destinacija (" + exclCount + " isk.)",
                    exclCount, BigDecimal.valueOf(exclFlat).divide(BigDecimal.valueOf(Math.max(1, exclCount)),
                            2, java.math.RoundingMode.HALF_UP),
                    BigDecimal.valueOf(exclFlat));
        }

        // 9. REVEAL_BOX - 100% Escapii, flat 35€.
        int reveal = nz(price.getRevealBoxTotal());
        if (reveal > 0) {
            addItem(booking, ItemType.REVEAL_BOX,
                    "Reveal Box (fizicka isporuka)",
                    1, BigDecimal.valueOf(reveal), BigDecimal.valueOf(reveal));
        }
    }

    private void addItem(Booking booking, ItemType type, String description,
                         int quantity, BigDecimal unitPrice, BigDecimal customerTotal) {
        BookingFinancialItem item = new BookingFinancialItem();
        item.setBooking(booking);
        item.setItemType(type);
        item.setAllocationType(type.getAllocationType());
        item.setDescription(description);
        item.setQuantity(quantity);
        item.setUnitCustomerPrice(unitPrice.setScale(2, java.math.RoundingMode.HALF_UP));
        item.setCustomerTotal(customerTotal.setScale(2, java.math.RoundingMode.HALF_UP));
        // agencyCost, flight/hotel ostaju null dok admin ne unese (za Escapii-only nije potrebno)
        // Escapii-only stavke ne blokiraju fakturisanje - AllocationType.ESCAPII_100
        // se u kalkulatoru ne testira na agencyCost.
        item.setAllocationType(type.getAllocationType());
        booking.getFinancialItems().add(item);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
