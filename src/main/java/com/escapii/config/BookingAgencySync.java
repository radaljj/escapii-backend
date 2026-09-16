package com.escapii.config;

import com.escapii.model.Agency;
import com.escapii.model.Booking;
import com.escapii.repository.BookingRepository;
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
 * Usklađuje snimak agencije na rezervaciji sa agencijom koja stoji na terminu.
 *
 * <p>Rezervacija pamti agenciju iz trenutka potvrde. Ako admin posle toga promeni agenciju na
 * terminu, od 2026-09-17 to odmah povlači i nefakturisane rezervacije
 * ({@code AdminService.assignAgencyToDate}). Ovde se pri startu poprave i one koje su ostale
 * neusklađene od ranije (Marko: termin prebačen na drugu agenciju, zbirna faktura videla 1 od 3).
 *
 * <p>Pravilo: dok rezervacija nije u zbirnoj fakturi, termin je izvor istine. Fakturisane se ne
 * diraju. Idempotentno; greška ide u AppError, ne obara start. Transakcija ručno
 * ({@link TransactionTemplate}) - poziv iz iste klase zaobilazi proxy.
 */
@Slf4j
@Component
public class BookingAgencySync {

    static final String IZVOR = "booking-agency-sync";

    private final BookingRepository bookingRepository;
    private final TransactionTemplate tx;
    private final ObjectProvider<AppErrorService> appErrorService;

    public BookingAgencySync(BookingRepository bookingRepository,
                             PlatformTransactionManager txManager,
                             ObjectProvider<AppErrorService> appErrorService) {
        this.bookingRepository = bookingRepository;
        this.tx = new TransactionTemplate(txManager);
        this.appErrorService = appErrorService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void naStartu() {
        try {
            Integer n = tx.execute(status -> primeni());
            if (n != null && n > 0) {
                log.warn("[Obračun] {} rezervacija usklađeno sa agencijom termina", n);
            }
        } catch (Exception e) {
            log.error("[Obračun] Usklađivanje agencije rezervacija nije uspelo: {}", e.toString());
            zabelezi(e);
        }
    }

    /** Radi unutar transakcije koju otvara {@link #naStartu()}. @return koliko je rezervacija usklađeno. */
    int primeni() {
        List<Booking> neuskladjene = bookingRepository.findAgencyOutOfSync();
        for (Booking b : neuskladjene) {
            Agency a = b.getSelectedDate().getAgency();
            log.info("[Obračun] {}: agencija '{}' → '{}' (usklađeno sa terminom {})",
                    b.getBookingRef(), b.getAgencyNameSnapshot(), a.getName(), b.getSelectedDate().getId());
            b.setAgencyIdSnapshot(a.getId());
            b.setAgencyNameSnapshot(a.getName());
            bookingRepository.save(b);
        }
        return neuskladjene.size();
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
