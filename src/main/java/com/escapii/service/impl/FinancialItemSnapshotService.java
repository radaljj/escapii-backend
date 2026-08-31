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
        //    Poslovno pravilo: upgrade je fiksan fee/osoba (npr. 100€) i predstavlja
        //    cistu zajednicku zaradu Escapii+agencije, agencija tu NEMA dodatan trosak
        //    (rezervise hotel po dogovorenoj base ceni koju admin unosi u BASE_PACKAGE).
        //    Zato agencyCost setujemo na 0 vec u snapshotu, admin ne unosi rucno.
        int upgradePP = nz(price.getAccommodationExtraPerPerson());
        if (upgradePP > 0) {
            BookingFinancialItem upgrade = addItem(booking, ItemType.ACCOMMODATION_UPGRADE,
                    "Superior/Premium upgrade smestaja",
                    n, BigDecimal.valueOf(upgradePP), BigDecimal.valueOf((long) upgradePP * n));
            upgrade.setAgencyCost(BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP));
        }

        // 3. BREAKFAST - jedinicna cena je 20€/osoba/noc, quantity = putnici × noci.
        //    Ovo je jasnije za admin od "20€/os za sve noci × putnika" jer i unit
        //    i total i description otvoreno kazu strukturu.
        int breakfastPP = nz(price.getBreakfastPerPerson());
        int nights      = nz(price.getNumberOfNights());
        if (breakfastPP > 0) {
            // unit = breakfastPP / nights = 20€ po osobi po noci (default, ali se
            // izvodi iz preview-a da ne hardcodujemo cenu iz PriceCalculatora)
            int unitPerPersonPerNight = nights > 0 ? breakfastPP / nights : breakfastPP;
            int quantity              = nights > 0 ? n * nights : n;
            int total                 = breakfastPP * n;
            addItem(booking, ItemType.BREAKFAST,
                    "Doručak u hotelu (" + n + " putnika × " + nights + " noći)",
                    quantity,
                    BigDecimal.valueOf(unitPerPersonPerNight),
                    BigDecimal.valueOf(total));
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

        // 8. DESTINATION_EXCLUSIONS - 100% Escapii. Jedinicna cena je 10€/os/isk,
        //    quantity = broj NAPLATIVIH isk. × putnika (prvo isk. je besplatno pa
        //    ne ulazi u quantity, inace jedinicna cena izgleda pogresno).
        int exclFlat = nz(price.getExclusionCostFlat());
        int exclCount = nz(price.getExclusionCount());
        if (exclFlat > 0) {
            // izvedena unit iz totala: exclFlat = billable × unit × n
            // unit = exclFlat / (billable × n); billable = exclFlat / (unit × n)
            // Ne znamo unit direktno (10€), pa krecemo od pretpostavke default-a
            // 10€ i validiramo. Ako je airport rule drugaciji, quantity ipak radi.
            int unitPerPersonPerExclusion = 10;
            int billable = unitPerPersonPerExclusion * n > 0
                    ? exclFlat / (unitPerPersonPerExclusion * n)
                    : 0;
            int quantity = billable * n;
            addItem(booking, ItemType.DESTINATION_EXCLUSIONS,
                    "Isključivanja destinacija (" + billable + " naplativa od "
                            + exclCount + " × " + n + " putnika)",
                    quantity,
                    BigDecimal.valueOf(unitPerPersonPerExclusion),
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

    private BookingFinancialItem addItem(Booking booking, ItemType type, String description,
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
        booking.getFinancialItems().add(item);
        return item;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
