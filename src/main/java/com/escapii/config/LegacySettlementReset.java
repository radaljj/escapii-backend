package com.escapii.config;

import com.escapii.model.Booking;
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

import java.util.List;

/**
 * Vraća u aktivan tok obračuna rezervacije koje su zaostale iz STAROG toka fakturisanja po
 * rezervaciji (pre zbirnih faktura, 2026-09-17): settlementStatus INVOICED/PAID/VOIDED, broj
 * ESC-AG-… upisan na samoj rezervaciji, a bez veze ka zbirnoj fakturi ({@code agency_invoice_id}).
 *
 * <p>Takva rezervacija je bila nevidljiva: nije ulazila u zbirnu fakturu (filter po statusu),
 * nije se prikazivala ni kao preskočena, a panel više ne prikazuje stari broj - pa je izgledalo
 * kao da faktura „preskače" rezervaciju. Ovde se stari tragovi brišu, a status se ponovo
 * izvodi iz kalkulatora (READY_FOR_INVOICE ili NEEDS_COSTS) - isto što radi storno zbirne fakture.
 *
 * <p>Idempotentno: posle prvog prolaza nema više takvih redova (novi tok uvek upisuje vezu ka
 * fakturi), pa je svaki sledeći start bez posla. Greška se beleži kao AppError, ne obara start.
 *
 * <p>Transakcija se otvara ručno ({@link TransactionTemplate}), ne anotacijom: poziv iz iste
 * klase zaobilazi proxy, pa bi kalkulator čitao lazy stavke rezervacije bez sesije
 * (LazyInitializationException - uhvaćeno u lokalnom E2E).
 */
@Slf4j
@Component
public class LegacySettlementReset {

    static final String IZVOR = "legacy-settlement-reset";

    private final BookingRepository bookingRepository;
    private final AgencySettlementCalculator calculator;
    private final TransactionTemplate tx;
    private final ObjectProvider<AppErrorService> appErrorService;

    public LegacySettlementReset(BookingRepository bookingRepository,
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
                log.warn("[Obračun] {} rezervacija iz starog toka fakturisanja vraćeno u red za zbirnu fakturu", n);
            }
        } catch (Exception e) {
            log.error("[Obračun] Reset starog toka fakturisanja nije uspeo: {}", e.toString());
            zabelezi(e);
        }
    }

    /** Radi unutar transakcije koju otvara {@link #naStartu()}. @return koliko je rezervacija vraćeno. */
    int primeni() {
        List<Booking> zaostale = bookingRepository.findLegacySettledWithoutInvoice();
        for (Booking b : zaostale) {
            String stariBroj = b.getAgencyInvoiceNumber();
            SettlementStatus stariStatus = b.getSettlementStatus();
            b.setAgencyInvoiceNumber(null);
            b.setAgencyInvoicedAt(null);
            b.setAgencyPaidAt(null);
            b.setAgencyVoidedAt(null);
            b.setAgencyVoidReason(null);
            b.setSettlementStatus(spreman(b) ? SettlementStatus.READY_FOR_INVOICE : SettlementStatus.NEEDS_COSTS);
            bookingRepository.save(b);
            log.info("[Obračun] {}: stari tok {} ({}) -> {}", b.getBookingRef(), stariStatus,
                    stariBroj == null ? "bez broja" : stariBroj, b.getSettlementStatus());
        }
        return zaostale.size();
    }

    private boolean spreman(Booking b) {
        try {
            return calculator.calculate(b).isReadyForInvoice();
        } catch (Exception e) {
            log.warn("[Obračun] {}: kalkulator nije uspeo ({}), status NEEDS_COSTS", b.getBookingRef(), e.toString());
            return false;
        }
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
