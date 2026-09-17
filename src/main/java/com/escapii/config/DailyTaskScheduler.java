package com.escapii.config;

import com.escapii.model.Booking;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.CustomDateInquiryRepository;
import com.escapii.service.BookingSchedulingService;
import com.escapii.service.email.DigestEmailService;
import com.escapii.service.impl.ConfirmationDocumentAutoSender;
import com.escapii.service.impl.ExpiredDateCleanup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Jutarnji krug.
 *
 * <p><b>Slanja (prognoza, reveal, dokumenti)</b> idu na svakih 30 minuta od 10:00 do 21:30
 * (Beograd). Prvi pokušaj je u 10:00 kao i ranije; svaki sledeći krug samo pokupi ono što nije
 * otišlo - server je bio dole u 10:00, deploy baš tada, Resend ili vremenski servis zakazao.
 * Dupli mejl nije moguć: „poslato" se upisuje u bazu tek posle uspešnog slanja, a slanje i upis
 * idu pod bravom na redu rezervacije, pod kojom se stanje ponovo čita iz baze - ni ručno slanje
 * iz panela ne može da se preklopi (vidi BookingSchedulingServiceImpl). Spring ne pokreće
 * isti cron zadatak preklopljeno - sledeći termin se računa tek kad prethodni završi.
 *
 * <p><b>Dnevni koraci (završavanje, upozorenja, digest, čišćenje, brisanje pasoša)</b> idu tačno
 * jednom dnevno, na prvom krugu posle 10:00 koji je server dočekao: krug se „prijavi" u tabeli
 * {@code scheduler_runs} atomskim UPDATE-om, pa ni restart ni dva servera ne mogu da ga pokrenu
 * dvaput istog dana. Ako je server bio dole u 10:00, dnevni koraci idu u 10:30. Ako prijava u
 * bazi ne prolazi, /api/health/jobs posle 11:00 javlja DNEVNI_KRUG_KASNI (JobHealthService).
 */
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
    private final com.escapii.passport.PassportRetentionService passportRetentionService;
    private final com.escapii.service.AppErrorService appErrorService;
    private final ExpiredDateCleanup expiredDateCleanup;
    private final JdbcTemplate jdbc;

    static final ZoneId    ZONA                  = ZoneId.of("Europe/Belgrade");
    public static final String DNEVNI_POSAO      = "daily";
    /** Prvi red ikad posle ovog vremena: današnji dnevni koraci su već odrađeni (stara verzija, 10:00). */
    static final LocalTime ROK_PRVOG_SEMENA      = LocalTime.of(10, 30);

    static final String GRESKA_PRIJAVA           = "Jutarnji krug: prijava dnevnog kruga";
    static final String GRESKA_NEMA_DESTINACIJE  = "Jutarnji krug: nema destinacije";
    /** Bez destinacije nema ni prognoze (T-7) - upozorenje kreće kad polazak uđe u tih 7 dana. */
    static final int NEMA_DESTINACIJE_DANA       = 7;
    /** Isti prag kao za nedostupnu prognozu: reveal je za dva dana, ovo je poslednji trenutak. */
    static final int NEMA_DESTINACIJE_HITNO_DANA = 3;

    private static final DateTimeFormatter POLAZAK_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy.");

    /** Kad je prethodni dnevni krug prošao - digest izveštava o slanjima od tada. */
    private volatile LocalDateTime prethodniDnevniKrug;

    /** Ritam kruga je podesiv (app.scheduler.cycle-cron) - lokalna proba ga pusta na svaki minut. */
    @Scheduled(cron = "${app.scheduler.cycle-cron:0 0/30 10-21 * * *}", zone = "Europe/Belgrade")
    public void runCycle() {
        runSendingSteps();
        if (preuzmiDnevniKrug(LocalDate.now(ZONA), LocalTime.now(ZONA))) {
            runDailySteps();
        }
    }

    /** Ceo krug odjednom, bez prijave u bazi - koriste ga testovi. */
    public void runDailyTasks() {
        runSendingSteps();
        runDailySteps();
    }

    /**
     * Idempotentna slanja - smeju da se ponavljaju u toku dana.
     *
     * Redosled je bitan: prognoza uvek mora stići PRE reveala, jer je pisana kao najava
     * ("kada dobiješ email sa otkrićem destinacije...") i namerno ne imenuje grad. Kod kasno
     * potvrđenih rezervacija oba padaju istog kruga, pa je ovo jedino što drži redosled -
     * lanac je sinhron. sendReveals i sam proverava da je prognoza stvarno poslata za taj
     * booking, pa neuspela prognoza samo odloži reveal za sledeći krug umesto da ga pusti prerano.
     *
     * Pozivi su namerno ispisani kao lambde sa punim izrazom, ne kao reference na metode:
     * strukturni testovi (ForecastBeforeRevealTest, ForecastRevealOrderTest,
     * RevealBoxDigitalRevealTest) čitaju ovaj izvor i traže doslovno "sendPendingForecasts()"
     * pre "sendPendingReveals()". To je zaštita od toga da neko kasnije zameni redosled.
     */
    void runSendingSteps() {
        korak("prognoze",  () -> schedulingService.sendPendingForecasts());
        korak("reveal",    () -> schedulingService.sendPendingReveals());
        // Retry za dokumente čije auto-slanje unutar sendPendingReveals ciklusa je puklo
        // (SMTP hiccup, race, restart). Bez ovoga box korisnik može da "propadne kroz mrežu" -
        // revealSentAt postavljen, dokument nikad ne krene.
        korak("dokumenti", () -> confirmationDocumentAutoSender.sendAllPending());
    }

    /** Jednom dnevno. Svaki korak je izolovan: greška u jednom ne sme pojesti ostatak. */
    void runDailySteps() {
        // cancelStalePendingBookings() je uklonjen - admin ručno potvrđuje ili otkazuje
        korak("zavrsavanje",     () -> schedulingService.completeFinishedBookings());
        korak("upozorenja",      this::warnMissingDestinations);
        korak("digest",          this::sendDigest);
        korak("cleanup termina", this::cleanupExpiredDates);
        korak("cleanup upita",   this::cleanupClosedInquiries);
        // Brojevi pasoša imaju rok: 30 dana posle povratka, otkazane čim prođe polazak.
        korak("brisanje pasosa", () -> passportRetentionService.purgeExpired(LocalDate.now()));
    }

    /**
     * Atomska prijava dnevnog kruga za dati dan: UPDATE prolazi samo prvom ko ga pozove tog
     * dana (i posle restarta, jer je stanje u bazi). Ako baza ne odgovori, dnevni koraci se
     * preskaču do sledećeg kruga: bez baze ni oni ne bi radili, a rezerva „po vremenu" bi ih
     * mogla pustiti dvaput istog dana (pad u 10:00 pa uspešna prijava u 10:30). Pad ide u AppError.
     */
    boolean preuzmiDnevniKrug(LocalDate danas, LocalTime sada) {
        try {
            osigurajRed(danas, sada);
            // Kad je prošli krug bio - digest gleda slanja od tada (ne „24h", jer bi sat pomeranja
            // između dva dana udvostručio ili izgubio jedan polusatni krug).
            List<java.sql.Timestamp> prethodni = jdbc.query(
                    "SELECT last_run_at FROM scheduler_runs WHERE job = ?",
                    (rs, i) -> rs.getTimestamp(1), DNEVNI_POSAO);
            LocalDateTime sadaTacno = LocalDateTime.now();
            int preuzeto = jdbc.update(
                    "UPDATE scheduler_runs SET last_run_date = ?, last_run_at = ? WHERE job = ? AND (last_run_date IS NULL OR last_run_date < ?)",
                    java.sql.Date.valueOf(danas), java.sql.Timestamp.valueOf(sadaTacno), DNEVNI_POSAO, java.sql.Date.valueOf(danas));
            if (preuzeto == 1) {
                prethodniDnevniKrug = prethodni.isEmpty() || prethodni.get(0) == null ? null : prethodni.get(0).toLocalDateTime();
                log.info("[Scheduler] Dnevni krug za {} preuzet u {} (prethodni: {})", danas, sada, prethodniDnevniKrug);
            }
            return preuzeto == 1;
        } catch (Exception e) {
            log.error("[Scheduler] Prijava dnevnog kruga nije uspela ({}) - dnevni koraci čekaju sledeći krug", e.toString());
            zabelezi(GRESKA_PRIJAVA, e);
            return false;
        }
    }

    /**
     * Red dnevnog kruga postoji od starta aplikacije, ne tek od prvog kruga: /api/health/jobs
     * gleda taj red, pa bi između deploya i prvog kruga (do 30 min) lažno javljao DNEVNI_KRUG_KASNI.
     * Ide posle ApplicationReadyEvent, dakle posle SchemaBootstrap-a koji pravi tabelu. Pad se
     * samo loguje - prvi krug ponovo pokušava (preuzmiDnevniKrug).
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void osigurajRedPriStartu() {
        try {
            osigurajRed(LocalDate.now(ZONA), LocalTime.now(ZONA));
        } catch (Exception e) {
            log.warn("[Scheduler] Red dnevnog kruga nije upisan pri startu ({}) - pokušaće prvi krug", e.toString());
        }
    }

    /**
     * INSERT ... ON CONFLICT DO NOTHING. Prvi red ikad (prvi start ove verzije): ako je već
     * prošlo 10:30, današnji dnevni koraci su najverovatnije već odrađeni (stara verzija ih je
     * puštala u 10:00) - ne ponavljati ih na dan deploya. Kad red postoji, ništa se ne menja.
     */
    private void osigurajRed(LocalDate danas, LocalTime sada) {
        if (sada.isBefore(ROK_PRVOG_SEMENA)) {
            jdbc.update("INSERT INTO scheduler_runs (job, last_run_date) VALUES (?, NULL) ON CONFLICT (job) DO NOTHING",
                    DNEVNI_POSAO);
        } else {
            jdbc.update("INSERT INTO scheduler_runs (job, last_run_date) VALUES (?, ?) ON CONFLICT (job) DO NOTHING",
                    DNEVNI_POSAO, java.sql.Date.valueOf(danas));
        }
    }

    /**
     * Pokreće jedan korak i hvata sve iz njega. Greška se loguje sa imenom koraka pa se u
     * logu odmah vidi šta je palo, a ostali koraci se svejedno izvrše. Samo log nije dovoljan:
     * server radi i health je zelen, pa pad niko ne vidi - zato ide i u AppError.
     */
    private void korak(String ime, Runnable posao) {
        try {
            posao.run();
        } catch (Exception e) {
            log.error("[Scheduler] Korak '{}' je pao - ostali koraci se nastavljaju: {}",
                    ime, e.toString(), e);
            zabelezi("Jutarnji krug: korak " + ime, e);
        }
    }

    private void zabelezi(String gde, Exception e) {
        try {
            appErrorService.record(gde, 0, e);
        } catch (Exception zabelezi) {
            log.warn("[Scheduler] AppError nije zabeležen ({}): {}", gde, zabelezi.toString());
        }
    }

    // ── Upozorenje: potvrđena rezervacija bez destinacije ────────────────────

    /** Poruka u tabu Greške; ponavljanja se broje, a poruka se osvežava na svaki dnevni krug. */
    static final class NemaDestinacije extends RuntimeException {
        NemaDestinacije(String poruka) { super(poruka); }
    }

    /** Poseban tip da tim dobije NOVI mejl kad postane hitno, a ne samo brojač na starom. */
    static final class NemaDestinacijeHitno extends RuntimeException {
        NemaDestinacijeHitno(String poruka) { super(poruka); }
    }

    /**
     * Bez unete destinacije jutarnji krug nema šta da pošalje, a do sada je to postajalo
     * vidljivo tek kad reveal zakasni (health u 10:30 na T-2). Ovde se javlja čim polazak
     * uđe u prozor prognoze (7 dana), a posebno kad ostane 3 dana ili manje.
     */
    void warnMissingDestinations() {
        LocalDate today = LocalDate.now();
        List<Booking> bez = bookingRepository.findConfirmedWithoutDestination(
                today, today.plusDays(NEMA_DESTINACIJE_DANA));
        if (bez.isEmpty()) {
            // Sve destinacije su unete: zatvori otvorena upozorenja, da sledeći slučaj opet
            // pošalje mejl timu (AppError šalje mejl samo za prvo pojavljivanje dok je otvoreno).
            resiUpozorenjaBezDestinacije();
            return;
        }

        LocalDate hitnoDo = today.plusDays(NEMA_DESTINACIJE_HITNO_DANA);
        List<Booking> hitne = bez.stream()
                .filter(b -> polazak(b) != null && !polazak(b).isAfter(hitnoDo))
                .toList();

        String spisak = bez.stream().map(DailyTaskScheduler::opis).collect(Collectors.joining(", "));
        log.warn("[Scheduler] Bez destinacije, polazak za <= {} dana: {}", NEMA_DESTINACIJE_DANA, spisak);
        zabelezi(GRESKA_NEMA_DESTINACIJE, new NemaDestinacije(
                "Bez destinacije, a polazak je za najviše " + NEMA_DESTINACIJE_DANA + " dana: " + spisak
                + ". Bez destinacije ne ide ni prognoza ni reveal - unesi je u panelu."));
        if (!hitne.isEmpty()) {
            String hitniSpisak = hitne.stream().map(DailyTaskScheduler::opis).collect(Collectors.joining(", "));
            zabelezi(GRESKA_NEMA_DESTINACIJE, new NemaDestinacijeHitno(
                    "HITNO: polazak za " + NEMA_DESTINACIJE_HITNO_DANA + " dana ili manje, a destinacija nije uneta: "
                    + hitniSpisak + ". Reveal ne može da ode."));
        }
    }

    private void resiUpozorenjaBezDestinacije() {
        try {
            appErrorService.getAll().stream()
                    .filter(e -> GRESKA_NEMA_DESTINACIJE.equals(e.getEndpoint()) && !e.isResolved())
                    .forEach(e -> appErrorService.resolve(e.getId()));
        } catch (Exception e) {
            log.warn("[Scheduler] Zatvaranje upozorenja 'nema destinacije' nije uspelo: {}", e.toString());
        }
    }

    private static LocalDate polazak(Booking b) {
        return b.getSelectedDate() != null ? b.getSelectedDate().getDepartureDate() : null;
    }

    private static String opis(Booking b) {
        LocalDate p = polazak(b);
        return b.getBookingRef() + (p != null ? " (polazak " + p.format(POLAZAK_FMT) + ")" : "");
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /**
     * Briše termine čiji je datum polaska prošao ILI je danas:
     * - bez rezervacija → briše se iz baze, zajedno sa vezama ka destinacijama (ExpiredDateCleanup)
     * - sa rezervacijama → deaktivira se (čuva istoriju)
     * Cutoff je "sutra" (ne "danas") jer BookingServiceImpl odbija rezervaciju
     * za termin koji je danas (isAfter(today) mora biti true) - termin sa
     * departureDate=danas se zato tretira kao već istekao za potrebe cleanup-a.
     *
     * <p>Deaktivacija ide PRVA: dira druge redove i ne sme da zavisi od brisanja. Dok je
     * brisanje padalo na FK iz term_destination, a išlo je prvo, preskakala se i ona.
     */
    public void cleanupExpiredDates() {
        LocalDate cutoff = LocalDate.now().plusDays(1);
        int deactivated = availableDateRepository.deactivateExpiredWithBookings(cutoff);
        if (deactivated > 0) {
            log.info("[Cleanup] Termini: deaktivirano={}", deactivated);
        }
        ExpiredDateCleanup.Rezultat obrisano = expiredDateCleanup.obrisiIstekleBezRezervacija(cutoff);
        if (obrisano.termina() > 0) {
            log.info("[Cleanup] Termini: obrisano={} (veza ka destinacijama: {}, iz stare tabele veza: {})",
                    obrisano.termina(), obrisano.veza(), obrisano.starihVeza());
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

        // Poslato od prethodnog dnevnog kruga (slanja se ponavljaju i popodne, pa prognoza
        // koja je otišla juče u 14:00 mora u današnji digest). Bez zapisa o prethodnom
        // (prvi start): poslednja 24 sata.
        LocalDateTime sada = LocalDateTime.now();
        LocalDateTime od   = prethodniDnevniKrug != null ? prethodniDnevniKrug : sada.minusHours(24);
        List<Booking> revealSent        = bookingRepository.findRevealSentBetween(od, sada);
        List<Booking> forecastSent      = bookingRepository.findForecastSentBetween(od, sada);
        List<Booking> upcoming          = bookingRepository.findConfirmedDepartingBetween(today, today.plusDays(14));
        // Reveal Box podsetnik - polazak od danas do +5 dana
        List<Booking> revealBoxPending  = bookingRepository.findPendingRevealBoxes(today, today.plusDays(5));
        // Korisnik otvorio reveal stranicu, dokument rezervacije još nije poslat -
        // tim treba da uploaduje PDF (slanje je automatsko posle upload-a)
        List<Booking> revealedAndViewed = bookingRepository.findRevealedAndViewed(today, today.plusDays(14));
        // Korisnik NIJE otvorio reveal, a polazak je za <= 2 dana - hitno upozorenje
        List<Booking> notViewedUrgent   = bookingRepository.findRevealedButNotViewed(today, today.plusDays(2));
        // Potvrđene bez destinacije sa polaskom za <= 7 dana - bez nje nema ni prognoze ni reveala
        List<Booking> missingDestination = bookingRepository.findConfirmedWithoutDestination(
                today, today.plusDays(NEMA_DESTINACIJE_DANA));

        if (!upcoming.isEmpty() || !revealSent.isEmpty() || !forecastSent.isEmpty()
                || !revealBoxPending.isEmpty() || !revealedAndViewed.isEmpty() || !notViewedUrgent.isEmpty()
                || !missingDestination.isEmpty()) {
            digestEmailService.sendDailyDigest(today, revealSent, forecastSent, upcoming,
                    revealBoxPending, revealedAndViewed, notViewedUrgent, missingDestination);
            log.info("[Scheduler] Digest poslan. Reveal: {}, Forecast: {}, Ukupno 14 dana: {}, RevealBox: {}, Viewed: {}, NotViewed urgent: {}, Bez destinacije: {}",
                    revealSent.size(), forecastSent.size(), upcoming.size(),
                    revealBoxPending.size(), revealedAndViewed.size(), notViewedUrgent.size(),
                    missingDestination.size());
        } else {
            log.info("[Scheduler] Nema aktivnih rezervacija - digest nije poslan.");
        }
    }
}
