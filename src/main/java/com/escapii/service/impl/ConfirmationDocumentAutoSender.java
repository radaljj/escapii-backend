package com.escapii.service.impl;

import com.escapii.model.Booking;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.RevealEventRepository;
import com.escapii.service.email.ConfirmationDocumentEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Centralno mesto za pravilo "sme li dokument da ide korisniku".
 *
 * Non-box rezervacija: mora postojati RevealEvent (kupac je otvorio reveal link
 *   i vec zna destinaciju). Bez toga dokument bi mejlom otkrio grad ranije nego
 *   sto to proizvod obecava.
 *
 * Reveal Box rezervacija: kutija je vec stigla na T-5..T-3 i sadrzi destinaciju,
 *   pa je dovoljno da je i digitalni reveal poslat (revealSentAt != null, T-2).
 *   Ne trazimo klik na link jer nema garancije da ce kupac uopste kliknuti -
 *   destinaciju je vec saznao iz kutije.
 *
 * Koristi se sa dva mesta:
 *  - AdminServiceImpl (upload/resend) - da centralizuje odluku
 *  - BookingSchedulingServiceImpl / DailyTaskScheduler - da posle svakog reveala
 *    pokusa da isporuci vec uploadovan dokument (auto-send), i da svakodnevno
 *    ponavlja neuspesno slanje (retry).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmationDocumentAutoSender {

    private final BookingRepository bookingRepository;
    private final RevealEventRepository revealEventRepository;
    private final ConfirmationDocumentEmailService confirmationDocumentEmailService;

    /**
     * Pravilo: box korisnik ceka revealSentAt (T-2), ostali cekaju da klikuu reveal link.
     * Ne dira bazu za konsultaciju - samo cita polja koja su na booking-u.
     * Provera RevealEvent-a ide u posebnom read-only query-ju.
     */
    public boolean canReceive(Booking booking) {
        if (Boolean.TRUE.equals(booking.getHasRevealBox())) {
            return booking.getRevealSentAt() != null;
        }
        return revealEventRepository.findByBookingRef(booking.getBookingRef()).isPresent();
    }

    /**
     * Ako je dokument uploadovan, jos nije poslat i korisnik sme da ga dobije -
     * salje odmah. Vreme se upisuje SAMO ako je mejl stvarno otisao (isti obrazac
     * kao AdminServiceImpl - inace panel laze da je stiglo).
     *
     * Vraca true ako je slanje uspelo u ovom pozivu, false u svim ostalim
     * slucajevima (nije bilo sta da se salje ILI je slanje puklo).
     */
    @Transactional
    public boolean sendIfReadyAndPending(Booking booking) {
        if (booking.getConfirmationDocument() == null) return false;
        if (booking.getConfirmationSentAt() != null) return false;
        if (!canReceive(booking)) return false;

        if (!confirmationDocumentEmailService.sendConfirmationDocument(booking)) {
            log.warn("[ConfirmationDocument] Auto-send pao za {} - ostaje neposlat, retry sutra",
                    booking.getBookingRef());
            return false;
        }
        booking.setConfirmationSentAt(LocalDateTime.now());
        bookingRepository.save(booking);
        log.info("[ConfirmationDocument] Auto-send za {} - dokument poslat", booking.getBookingRef());
        return true;
    }

    /**
     * Dnevni retry: prolazi kroz sve pending rezervacije (dokument postoji,
     * confirmationSentAt null, uslov reveal-a ispunjen) i pokusava da posalje.
     * Sluzi kao safety net za slucaj kada auto-send u istom cycle-u sa reveal-om
     * pukne (SMTP hiccup) ili scheduler restartuje pre poziva.
     */
    @Transactional
    public int sendAllPending() {
        List<Booking> pending = bookingRepository.findPendingConfirmationDocuments(java.time.LocalDate.now());
        int sent = 0;
        for (Booking b : pending) {
            if (sendIfReadyAndPending(b)) sent++;
        }
        if (!pending.isEmpty()) {
            log.info("[ConfirmationDocument] Retry pass: {}/{} poslato", sent, pending.size());
        }
        return sent;
    }
}
