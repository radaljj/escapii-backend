package com.escapii.config;

import com.escapii.model.Booking;
import com.escapii.model.BookingFinancialItem;
import com.escapii.model.ItemType;
import com.escapii.model.SettlementStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.service.AgencySettlementCalculator;
import com.escapii.service.AppErrorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Prepakuje doplatu za solo putnika iz zasebne stavke SOLO_SURCHARGE u BASE_PACKAGE
 * (promena od 2026-09-21). Tih 60 € je razlika za jednokrevetnu sobu koju agencija stvarno
 * naplati, pa mora da stoji na stavci gde se unosi trosak agencije i gde se marza deli 50/50.
 * Kao zasebna Escapii stavka pravila je laznu negativnu marzu na paketu i pogresnu podelu.
 *
 * <p>Dira samo rezervacije koje jos nisu u zbirnoj fakturi ({@code agencyInvoice IS NULL}) -
 * ono sto je vec fakturisano ostaje onako kako je fakturisano.
 *
 * <p>Novac se ne gubi ni ne stvara: iznos se premesta sa stavke na stavku, pa zbir stavki i
 * dalje mora biti jednak bruto vrednosti rezervacije. Marza paketa moze samo da poraste, zato
 * se rezervacija koja je bila blokirana negativnom marzom ovde moze vratiti u READY_FOR_INVOICE.
 *
 * <p>Idempotentno: posle prvog prolaza takvih stavki vise nema, a snapshot novih rezervacija ih
 * ne pravi. Greska se belezi kao AppError i ne obara start - isto kao {@link LegacySettlementReset},
 * od kojeg je preuzet i rucni {@link TransactionTemplate} (poziv iz iste klase zaobilazi proxy, pa
 * bi kalkulator citao lazy stavke bez sesije).
 */
@Slf4j
@Component
public class SoloSurchargeMerge {

    static final String IZVOR = "solo-surcharge-merge";

    private final BookingRepository bookingRepository;
    private final AgencySettlementCalculator calculator;
    private final TransactionTemplate tx;
    private final ObjectProvider<AppErrorService> appErrorService;

    public SoloSurchargeMerge(BookingRepository bookingRepository,
                              AgencySettlementCalculator calculator,
                              PlatformTransactionManager txManager,
                              ObjectProvider<AppErrorService> appErrorService) {
        this.bookingRepository = bookingRepository;
        this.calculator = calculator;
        this.tx = new TransactionTemplate(txManager);
        this.appErrorService = appErrorService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void naStartu() {
        try {
            Integer n = tx.execute(status -> primeni());
            if (n != null && n > 0) {
                log.warn("[Obračun] Doplata za solo putnika prepakovana u osnovni paket na {} rezervacija", n);
            }
        } catch (Exception e) {
            log.error("[Obračun] Prepakivanje solo doplate nije uspelo: {}", e.toString());
            zabelezi(e);
        }
    }

    /** Radi unutar transakcije koju otvara {@link #naStartu()}. @return koliko je rezervacija promenjeno. */
    int primeni() {
        List<Booking> stare = bookingRepository.findWithSoloSurchargeItem();
        int promenjeno = 0;
        for (Booking b : stare) {
            if (prepakuj(b)) {
                osveziStatus(b);
                bookingRepository.save(b);
                promenjeno++;
            }
        }
        return promenjeno;
    }

    /** @return true ako je rezervacija stvarno promenjena. */
    private boolean prepakuj(Booking b) {
        BookingFinancialItem paket = stavka(b, ItemType.BASE_PACKAGE);
        if (paket == null) {
            // Bez osnovnog paketa nema gde da ode - radije ostavi kako jeste nego da iznos nestane.
            log.warn("[Obračun] {}: solo doplata ostaje zasebna, rezervacija nema stavku BASE_PACKAGE",
                    b.getBookingRef());
            return false;
        }

        BigDecimal doplata = BigDecimal.ZERO;
        List<BookingFinancialItem> solo = b.getFinancialItems().stream()
                .filter(i -> i.getItemType() == ItemType.SOLO_SURCHARGE)
                .toList();
        for (BookingFinancialItem i : solo) {
            doplata = doplata.add(i.getCustomerTotal() == null ? BigDecimal.ZERO : i.getCustomerTotal());
        }
        // orphanRemoval na Booking.financialItems brise redove iz baze
        b.getFinancialItems().removeAll(solo);
        if (doplata.signum() == 0) return true;

        BigDecimal noviTotal = novac(paket.getCustomerTotal()).add(doplata);
        paket.setCustomerTotal(noviTotal);
        // Jedinicna cena ostaje total/kolicina; solo postoji samo kad je putnik jedan.
        if (paket.getQuantity() != null && paket.getQuantity() == 1) {
            paket.setUnitCustomerPrice(noviTotal);
        }
        paket.setDescription("Osnovni paket (let + hotel, uklj. doplatu za solo putnika "
                + doplata.setScale(0, RoundingMode.HALF_UP).toPlainString() + " €)");
        log.info("[Obračun] {}: solo doplata {} € prebačena u osnovni paket (sada {} €)",
                b.getBookingRef(), doplata.toPlainString(), noviTotal.toPlainString());
        return true;
    }

    /** Marza paketa je porasla, pa rezervacija blokirana negativnom marzom sada moze biti spremna. */
    private void osveziStatus(Booking b) {
        if (b.getSettlementStatus() != SettlementStatus.NEEDS_COSTS) return;
        try {
            if (calculator.calculate(b).isReadyForInvoice()) {
                b.setSettlementStatus(SettlementStatus.READY_FOR_INVOICE);
            }
        } catch (Exception e) {
            log.warn("[Obračun] {}: kalkulator nije uspeo posle prepakivanja ({}), status ostaje {}",
                    b.getBookingRef(), e.toString(), b.getSettlementStatus());
        }
    }

    private static BookingFinancialItem stavka(Booking b, ItemType tip) {
        return b.getFinancialItems().stream()
                .filter(i -> i.getItemType() == tip)
                .findFirst().orElse(null);
    }

    private static BigDecimal novac(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
    }

    private void zabelezi(Exception e) {
        try {
            AppErrorService svc = appErrorService.getIfAvailable();
            if (svc != null) svc.record(IZVOR, 0, e);
        } catch (Exception ex) {
            log.warn("[Obračun] AppError nije zabeležen: {}", ex.toString());
        }
    }
}
