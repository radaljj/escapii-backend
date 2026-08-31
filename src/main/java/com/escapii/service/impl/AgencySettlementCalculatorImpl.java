package com.escapii.service.impl;

import com.escapii.dto.AgencySettlementResponse;
import com.escapii.dto.AgencySettlementResponse.LineItem;
import com.escapii.dto.AgencySettlementResponse.LineStatus;
import com.escapii.dto.AgencySettlementResponse.WhoPaysWhom;
import com.escapii.model.AllocationType;
import com.escapii.model.Booking;
import com.escapii.model.BookingFinancialItem;
import com.escapii.model.BookingStatus;
import com.escapii.service.AgencySettlementCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementacija obracuna. Sve iznose racuna u {@link BigDecimal}, sa scale=2
 * i {@link RoundingMode#HALF_DOWN} za Escapii deo marze. Time se garantuju dva
 * invarijanta:
 *
 * <ol>
 *   <li>{@code escapiiShare + agencyShare == margin} - neparni cent ide agenciji
 *       jer se agencijski deo racuna kao ostatak, ne odvojenim zaokruzivanjem.</li>
 *   <li>{@code escapiiEarnings + agencyRetainedAmount == grossBookingValue} - ceo
 *       novac koji je usao iz kupca (kes + vaucer) je alociran, nista ne curi.</li>
 * </ol>
 *
 * <p>Vaucer je poseban - podela marze se racuna na bruto vrednost (totalPriceAll
 * + voucherDiscount), a onda se {@code netSettlement} korekcijom pravi za novac
 * koji Escapii vec drzi. Videti {@link #calculate(Booking)} javadoc.
 */
@Service
public class AgencySettlementCalculatorImpl implements AgencySettlementCalculator {

    private static final int MONEY_SCALE = 2;
    /** Escapii deo se zaokruzuje HALF_DOWN da neparni cent pripadne agenciji. */
    private static final RoundingMode ESCAPII_ROUNDING = RoundingMode.HALF_DOWN;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    @Override
    public AgencySettlementResponse calculate(Booking booking) {
        List<LineItem> lineItems = new ArrayList<>();
        List<String> validationErrors = new ArrayList<>();

        // Sve agregate drzimo na scale=2 od pocetka - JSON serializacija i testovi
        // porede po vrednosti+skali, pa neka svi izadju sa "0.00" ne "0".
        BigDecimal sharedRevenue         = BigDecimal.ZERO.setScale(MONEY_SCALE);
        BigDecimal agencyCostsTotal      = BigDecimal.ZERO.setScale(MONEY_SCALE);
        BigDecimal escapiiSharedMargin   = BigDecimal.ZERO.setScale(MONEY_SCALE);
        BigDecimal agencyMarginPart      = BigDecimal.ZERO.setScale(MONEY_SCALE);
        BigDecimal escapiiExclusiveRevenue = BigDecimal.ZERO.setScale(MONEY_SCALE);
        BigDecimal lineItemsCustomerSum  = BigDecimal.ZERO.setScale(MONEY_SCALE);

        boolean anyMissingCost = false;
        boolean anyNegativeMargin = false;

        List<BookingFinancialItem> items = booking.getFinancialItems() == null
                ? List.of()
                : booking.getFinancialItems();

        for (BookingFinancialItem item : items) {
            LineItem line = buildLineItem(item);
            lineItems.add(line);
            lineItemsCustomerSum = lineItemsCustomerSum.add(nz(item.getCustomerTotal()));

            switch (item.getAllocationType()) {
                case MARGIN_50_50 -> {
                    sharedRevenue = sharedRevenue.add(nz(item.getCustomerTotal()));
                    if (item.getAgencyCost() == null) {
                        anyMissingCost = true;
                        // marza jos ne moze da se izracuna, ali stavka i dalje ide u agregat
                    } else {
                        BigDecimal cost = money(item.getAgencyCost());
                        agencyCostsTotal = agencyCostsTotal.add(cost);
                        BigDecimal margin = money(item.getCustomerTotal()).subtract(cost);
                        if (margin.signum() < 0) {
                            anyNegativeMargin = true;
                            validationErrors.add("Negativna marža na stavci "
                                    + item.getItemType() + ": " + margin + " € - proveri troskove.");
                        }
                        escapiiSharedMargin = escapiiSharedMargin.add(line.getEscapiiShare());
                        agencyMarginPart = agencyMarginPart.add(line.getAgencyShare());
                    }
                }
                case ESCAPII_100 -> escapiiExclusiveRevenue =
                        escapiiExclusiveRevenue.add(nz(item.getCustomerTotal()));
                case AGENCY_100 -> {
                    // Zadrzano za buducnost; trenutno nijedna stavka nije 100% agencija.
                }
            }
        }

        BigDecimal voucherDiscount = money(BigDecimal.valueOf(nz(booking.getVoucherDiscount())));
        BigDecimal totalPriceAll = money(BigDecimal.valueOf(nz(booking.getTotalPriceAll())));
        BigDecimal grossBookingValue = totalPriceAll.add(voucherDiscount);
        BigDecimal customerCash = totalPriceAll;

        // Reconciliation: zbir stavki mora biti jednak bruto vrednosti rezervacije.
        boolean reconciledLineItems = lineItemsCustomerSum.compareTo(grossBookingValue) == 0;
        if (!reconciledLineItems && !items.isEmpty()) {
            validationErrors.add("Zbir stavki (" + lineItemsCustomerSum
                    + " €) ne poklapa se sa bruto vrednoscu rezervacije (" + grossBookingValue + " €).");
        }

        BigDecimal escapiiEarnings = escapiiSharedMargin.add(escapiiExclusiveRevenue);
        BigDecimal agencyRetained = agencyCostsTotal.add(agencyMarginPart);
        // Napomena: agencyRetained ne ukljucuje ESCAPII_100 stavke - agencija taj novac
        // vec nije primila (Escapii ga fakturise ili je vec dobio kao vaucer).

        BigDecimal netSettlement = escapiiEarnings.subtract(voucherDiscount);
        WhoPaysWhom whoPaysWhom = resolveWhoPaysWhom(netSettlement);

        if (anyMissingCost) {
            validationErrors.add("Nedostaju troskovi agencije za neke 50/50 stavke - obracun nije spreman za fakturu.");
        }

        boolean overallReconciled = reconciledLineItems && !anyMissingCost;

        // Booking mora biti CONFIRMED da bi obracun bio spreman za fakturu.
        // PENDING = kupac jos nije platio; CANCELLED = necega necega vise nema.
        // Admin sme unapred da unese troskove, ali dok status nije CONFIRMED
        // ne treba se prelaziti u READY_FOR_INVOICE ni dozvoliti finalize.
        boolean confirmedBooking = booking.getStatus() == BookingStatus.CONFIRMED;
        if (!confirmedBooking && !items.isEmpty()) {
            validationErrors.add("Rezervacija nije CONFIRMED (trenutno "
                    + booking.getStatus() + ") - fakturisanje agenciji nije dozvoljeno.");
        }

        boolean readyForInvoice = overallReconciled && !anyNegativeMargin
                && !items.isEmpty() && confirmedBooking;

        return AgencySettlementResponse.builder()
                .bookingId(booking.getId())
                .bookingRef(booking.getBookingRef())
                .agencyId(booking.getAgencyIdSnapshot())
                .agencyName(booking.getAgencyNameSnapshot())
                .currency("EUR")
                .settlementStatus(booking.getSettlementStatus())
                .agencyInvoiceNumber(booking.getAgencyInvoiceNumber())
                .agencyInvoicedAt(booking.getAgencyInvoicedAt())
                .agencyPaidAt(booking.getAgencyPaidAt())
                .lineItems(lineItems)
                .grossBookingValue(grossBookingValue)
                .customerCashAmount(customerCash)
                .voucherAmount(voucherDiscount)
                .sharedRevenueTotal(sharedRevenue)
                .agencyCostsTotal(agencyCostsTotal)
                .sharedMarginTotal(sharedRevenue.subtract(agencyCostsTotal))
                .escapiiSharedMarginPart(escapiiSharedMargin)
                .agencyMarginPart(agencyMarginPart)
                .escapiiExclusiveRevenue(escapiiExclusiveRevenue)
                .escapiiEarnings(escapiiEarnings)
                .voucherApplied(voucherDiscount)
                .netSettlement(netSettlement)
                .whoPaysWhom(whoPaysWhom)
                .agencyRetainedAmount(agencyRetained)
                .reconciled(overallReconciled)
                .readyForInvoice(readyForInvoice)
                .validationErrors(validationErrors)
                .build();
    }

    private LineItem buildLineItem(BookingFinancialItem item) {
        BigDecimal customerTotal = money(item.getCustomerTotal());
        BigDecimal agencyCost = item.getAgencyCost() == null ? null : money(item.getAgencyCost());

        BigDecimal margin;
        BigDecimal escapiiShare;
        BigDecimal agencyShare;
        LineStatus status;

        if (item.getAllocationType() == AllocationType.ESCAPII_100) {
            margin = null;
            escapiiShare = customerTotal;
            agencyShare = BigDecimal.ZERO.setScale(MONEY_SCALE);
            status = LineStatus.OK;
        } else if (item.getAllocationType() == AllocationType.AGENCY_100) {
            margin = null;
            escapiiShare = BigDecimal.ZERO.setScale(MONEY_SCALE);
            agencyShare = customerTotal;
            status = LineStatus.OK;
        } else { // MARGIN_50_50
            if (agencyCost == null) {
                margin = null;
                escapiiShare = BigDecimal.ZERO.setScale(MONEY_SCALE);
                agencyShare = BigDecimal.ZERO.setScale(MONEY_SCALE);
                status = LineStatus.MISSING_COST;
            } else {
                margin = customerTotal.subtract(agencyCost);
                escapiiShare = margin.divide(TWO, MONEY_SCALE, ESCAPII_ROUNDING);
                agencyShare = margin.subtract(escapiiShare);
                status = margin.signum() < 0 ? LineStatus.NEGATIVE_MARGIN : LineStatus.OK;
            }
        }

        return LineItem.builder()
                .itemType(item.getItemType())
                .allocationType(item.getAllocationType())
                .description(item.getDescription())
                .quantity(item.getQuantity())
                .unitCustomerPrice(money(item.getUnitCustomerPrice()))
                .customerTotal(customerTotal)
                .agencyCost(agencyCost)
                .flightAgencyCost(item.getFlightAgencyCost() == null
                        ? null : money(item.getFlightAgencyCost()))
                .hotelAgencyCost(item.getHotelAgencyCost() == null
                        ? null : money(item.getHotelAgencyCost()))
                .margin(margin)
                .escapiiShare(escapiiShare)
                .agencyShare(agencyShare)
                .status(status)
                .build();
    }

    private WhoPaysWhom resolveWhoPaysWhom(BigDecimal netSettlement) {
        int cmp = netSettlement.signum();
        if (cmp > 0) return WhoPaysWhom.AGENCY_PAYS_ESCAPII;
        if (cmp < 0) return WhoPaysWhom.ESCAPII_PAYS_AGENCY;
        return WhoPaysWhom.NO_TRANSFER;
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(MONEY_SCALE) : v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
