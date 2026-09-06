package com.escapii.config;

import com.escapii.model.Booking;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.CustomDateInquiryRepository;
import com.escapii.service.BookingSchedulingService;
import com.escapii.service.email.DigestEmailService;
import com.escapii.service.impl.ConfirmationDocumentAutoSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyTaskScheduler {

    private final BookingSchedulingService     schedulingService;
    private final BookingRepository            bookingRepository;
    private final DigestEmailService           digestEmailService;
    private final AvailableDateRepository      availableDateRepository;
    private final CustomDateInquiryRepository  inquiryRepository;
    private final ConfirmationDocumentAutoSender confirmationDocumentAutoSender;

    @Scheduled(cron = "0 0 10 * * *", zone = "Europe/Belgrade")
    public void runDailyTasks() {
        // Redosled je bitan: prognoza uvek mora stići PRE reveala, jer je pisana
        // kao najava ("kada dobiješ email sa otkrićem destinacije...") i namerno
        // ne imenuje grad. Kod kasno potvrđenih rezervacija oba padaju istog
        // dana, pa je ovo jedino što drži redosled - lanac je sinhron.
        //
        // Svaki korak je izolovan: greška u jednom ne sme pojesti ostatak dana.
        // Ranije je izuzetak u prvom koraku značio da tog dana nema ni reveala, ni
        // dokumenata, ni digesta - i to bez ijednog traga osim prekinutog loga.
        // Redosled ostaje isti, a izolacija ga ne kvari: sendReveals i sam proverava
        // da je prognoza stvarno poslata za taj booking, pa neuspela prognoza samo
        // odloži reveal za sutra umesto da ga pusti prerano.
        //
        // Pozivi su namerno ispisani kao lambde sa punim izrazom, ne kao reference na
        // metode: strukturni testovi (ForecastBeforeRevealTest, ForecastRevealOrderTest,
        // RevealBoxDigitalRevealTest) čitaju ovaj izvor i traže doslovno
        // "sendPendingForecasts()" pre "sendPendingReveals()". To je zaštita od toga da
        // neko kasnije zameni redosled - i mora ostati čitljiva iz teksta.
        korak("prognoze",        () -> schedulingService.sendPendingForecasts());
        korak("reveal",          () -> schedulingService.sendPendingReveals());
        // Retry za dokumente cije auto-slanje unutar sendPendingReveals cycle-a
        // je puklo (SMTP hiccup, race, restart). Bez ovoga box korisnik moze da
        // "propadne kroz mrezu" - revealSentAt postavljen, dokument nikad ne krene.
        korak("dokumenti",       () -> confirmationDocumentAutoSender.sendAllPending());
        // cancelStalePendingBookings() je uklonjen - admin ručno potvrđuje ili otkazuje
        korak("zavrsavanje",     () -> schedulingService.completeFinishedBookings());
        korak("digest",          this::sendDigest);
        korak("cleanup termina", this::cleanupExpiredDates);
        korak("cleanup upita",   this::cleanupClosedInquiries);
    }

    /**
     * Pokreće jedan dnevni korak i hvata sve iz njega. Greška se loguje sa imenom
     * koraka pa se u logu odmah vidi šta je palo, a ostali koraci se svejedno izvrše.
     */
    private void korak(String ime, Runnable posao) {
        try {
            posao.run();
        } catch (Exception e) {
            log.error("[Scheduler] Korak '{}' je pao - ostali koraci se nastavljaju: {}",
                    ime, e.toString(), e);
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /**
     * Briše termine čiji je datum polaska prošao ILI je danas:
     * - bez rezervacija → briše se iz baze
     * - sa rezervacijama → deaktivira se (čuva istoriju)
     * Cutoff je "sutra" (ne "danas") jer BookingServiceImpl odbija rezervaciju
     * za termin koji je danas (isAfter(today) mora biti true) - termin sa
     * departureDate=danas se zato tretira kao već istekao za potrebe cleanup-a.
     */
    public void cleanupExpiredDates() {
        LocalDate cutoff = LocalDate.now().plusDays(1);
        int deleted     = availableDateRepository.deleteExpiredWithNoBookings(cutoff);
        int deactivated = availableDateRepository.deactivateExpiredWithBookings(cutoff);
        if (deleted > 0 || deactivated > 0) {
            log.info("[Cleanup] Termini: obrisano={}, deaktivirano={}", deleted, deactivated);
        }
    }

    /**
     * Briše zatvorene upite (status=CLOSED) koji su zatvoreni pre više od 24 sata.
     */
    public void cleanupClosedInquiries() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        int deleted = inquiryRepository.deleteClosedBefore(cutoff);
        if (deleted > 0) {
            log.info("[Cleanup] Upiti: obrisano {} zatvorenih upita starijih od 24h", deleted);
        }
    }

    public Map<String, String> sendRevealForBooking(Long id, String url) {
        return schedulingService.sendRevealForBooking(id, url);
    }

    public Map<String, String> sendForecastForBooking(Long id) {
        return schedulingService.sendForecastForBooking(id);
    }

    // ── Digest helper ─────────────────────────────────────────────────────────

    private void sendDigest() {
        LocalDate today = LocalDate.now();

        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay   = today.plusDays(1).atStartOfDay();
        List<Booking> revealSent        = bookingRepository.findRevealSentBetween(startOfDay, endOfDay);
        List<Booking> forecastSent      = bookingRepository.findForecastSentBetween(startOfDay, endOfDay);
        List<Booking> upcoming          = bookingRepository.findConfirmedDepartingBetween(today, today.plusDays(14));
        // Reveal Box podsetnik - polazak od danas do +5 dana
        List<Booking> revealBoxPending  = bookingRepository.findPendingRevealBoxes(today, today.plusDays(5));
        // Korisnik otvorio reveal stranicu, dokument rezervacije još nije poslat -
        // tim treba da uploaduje PDF (slanje je automatsko posle upload-a)
        List<Booking> revealedAndViewed = bookingRepository.findRevealedAndViewed(today, today.plusDays(14));
        // Korisnik NIJE otvorio reveal, a polazak je za <= 2 dana - hitno upozorenje
        List<Booking> notViewedUrgent   = bookingRepository.findRevealedButNotViewed(today, today.plusDays(2));

        if (!upcoming.isEmpty() || !revealSent.isEmpty() || !forecastSent.isEmpty()
                || !revealBoxPending.isEmpty() || !revealedAndViewed.isEmpty() || !notViewedUrgent.isEmpty()) {
            digestEmailService.sendDailyDigest(today, revealSent, forecastSent, upcoming,
                    revealBoxPending, revealedAndViewed, notViewedUrgent);
            log.info("[Scheduler] Digest poslan. Reveal: {}, Forecast: {}, Ukupno 14 dana: {}, RevealBox: {}, Viewed: {}, NotViewed urgent: {}",
                    revealSent.size(), forecastSent.size(), upcoming.size(),
                    revealBoxPending.size(), revealedAndViewed.size(), notViewedUrgent.size());
        } else {
            log.info("[Scheduler] Nema aktivnih rezervacija - digest nije poslan.");
        }
    }
}
