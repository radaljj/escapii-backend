package com.escapii.service.impl;

import com.escapii.model.Booking;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.RevealEventRepository;
import com.escapii.service.AppErrorService;
import com.escapii.service.email.ConfirmationDocumentEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 *    pokusa da isporuci vec uploadovan dokument (auto-send), i da u svakom krugu
 *    (30 min) ponavlja neuspesno slanje (retry).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmationDocumentAutoSender {

    /** Mesto u tabu Greške za pad slanja dokumenta - isto za sve putanje (upload, grebanje, krug). */
    public static final String GRESKA_SLANJA = "Dokument rezervacije: slanje";

    private static final DateTimeFormatter POLAZAK_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy.");

    private final BookingRepository bookingRepository;
    private final RevealEventRepository revealEventRepository;
    private final ConfirmationDocumentEmailService confirmationDocumentEmailService;
    private final AppErrorService appErrorService;

    /** Poseban tip: AppError grupiše po mestu i tipu, pa ovo ostaje odvojeno od ostalih grešaka. */
    public static final class DokumentNijePoslat extends RuntimeException {
        DokumentNijePoslat(String poruka) { super(poruka); }
    }

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
    public boolean sendIfReadyAndPending(Booking booking) {
        if (booking.getConfirmationDocument() == null) return false;
        if (booking.getConfirmationSentAt() != null) return false;
        if (!canReceive(booking)) return false;

        if (!confirmationDocumentEmailService.sendConfirmationDocument(booking)) {
            log.warn("[ConfirmationDocument] Auto-send pao za {} - ostaje neposlat, ponavlja se u sledecem krugu",
                    booking.getBookingRef());
            prijaviPadSlanja(booking);
            return false;
        }
        // Ciljani upis, ne save(). Booking ovamo stiže DETACHED iz scheduler petlje
        // koja nema transakciju, pa bi save() bio merge i prepisao bi sve kolone
        // vrednostima od trenutka kad je lista učitana - uključujući i sam PDF koji
        // upravo šaljemo. Videti BookingRepository.markConfirmationSent.
        // NAMERNO bez booking.setConfirmationSentAt(...): iz sendAllPending rezervacija
        // stize kao MANAGED entitet, i setter bi je zaprljao - Hibernate bi na commit-u
        // upisao CEO red iz snapshot-a starog koliko i cela petlja slanja. To je isti
        // bag koji ciljani upit resava, samo drugim putem. Niko posle ovoga ne cita
        // polje sa objekta; vraceni boolean je jedini signal koji pozivaoci koriste.
        bookingRepository.markConfirmationSent(booking.getId(), LocalDateTime.now());
        log.info("[ConfirmationDocument] Auto-send za {} - dokument poslat", booking.getBookingRef());
        return true;
    }

    /**
     * Retry u svakom krugu: prolazi kroz sve pending rezervacije (dokument postoji,
     * confirmationSentAt null, uslov reveal-a ispunjen) i pokusava da posalje.
     * Sluzi kao safety net za slucaj kada auto-send u istom cycle-u sa reveal-om
     * pukne (SMTP hiccup) ili scheduler restartuje pre poziva.
     */
    public int sendAllPending() {
        List<Booking> pending = bookingRepository.findPendingConfirmationDocuments(LocalDate.now());
        int sent = 0;
        for (Booking b : pending) {
            if (sendIfReadyAndPending(b)) sent++;
        }
        if (!pending.isEmpty()) {
            log.info("[ConfirmationDocument] Retry pass: {}/{} poslato", sent, pending.size());
        }
        // Svaki neposlat dokument je na ovoj listi, pa prolaz bez ijednog pada znači da više
        // nema šta da se prijavljuje: otvorene prijave se zatvaraju, da SLEDEĆI pad opet pošalje
        // mejl timu (AppError šalje mejl samo za prvo pojavljivanje dok je red otvoren).
        if (sent == pending.size()) zatvoriPrijavePada();
        return sent;
    }

    /**
     * Pad slanja dokumenta ide u AppError, kao i pad prognoze i reveala. Samo log nije dovoljan:
     * kupac je video destinaciju, a karte i smeštaj mu ne stižu, i to niko ne vidi. Zovu ga sve
     * putanje koje šalju bez admina na vezi (upload i krug kroz ovu klasu, grebanje kroz
     * RevealServiceImpl); ručno slanje iz panela grešku već prikazuje adminu. Beleženje nikad
     * ne sme da obori pozivaoca.
     */
    public void prijaviPadSlanja(Booking booking) {
        try {
            LocalDate polazak = booking.getSelectedDate() != null
                    ? booking.getSelectedDate().getDepartureDate() : null;
            appErrorService.record(GRESKA_SLANJA, 0, new DokumentNijePoslat(
                    "Dokument rezervacije nije poslat za " + booking.getBookingRef()
                    + (polazak != null ? ", polazak " + polazak.format(POLAZAK_FMT) : "")
                    + " - mejl sa PDF-om nije otišao. Slanje se samo ponavlja na svakih 30 min "
                    + "(10:00-21:30); može i ručno iz panela (Pošalji ponovo)."));
        } catch (Exception e) {
            log.warn("[ConfirmationDocument] AppError nije zabeležen za {}: {}", booking.getBookingRef(), e.toString());
        }
    }

    private void zatvoriPrijavePada() {
        try {
            if (appErrorService.countUnresolved() == 0) return;
            appErrorService.getAll().stream()
                    .filter(e -> GRESKA_SLANJA.equals(e.getEndpoint()) && !e.isResolved())
                    .forEach(e -> appErrorService.resolve(e.getId()));
        } catch (Exception e) {
            log.warn("[ConfirmationDocument] Zatvaranje prijava pada nije uspelo: {}", e.toString());
        }
    }
}
