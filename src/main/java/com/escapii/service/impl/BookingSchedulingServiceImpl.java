package com.escapii.service.impl;

import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.VoucherStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.service.AppErrorService;
import com.escapii.service.BookingSchedulingService;
import com.escapii.service.email.ForecastEmailService;
import com.escapii.service.email.RevealEmailService;
import com.escapii.service.weather.DailyForecast;
import com.escapii.service.weather.WeatherService;
import com.escapii.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingSchedulingServiceImpl implements BookingSchedulingService {

    private final BookingRepository    bookingRepository;
    private final GiftVoucherRepository giftVoucherRepository;
    private final RevealEmailService   revealEmailService;
    private final ForecastEmailService forecastEmailService;
    private final WeatherService       weatherService;
    private final ConfirmationDocumentAutoSender confirmationDocumentAutoSender;
    private final VoucherLedger        voucherLedger;
    private final AppErrorService      appErrorService;
    private final org.springframework.transaction.PlatformTransactionManager txManager;

    /**
     * Kratka transakcija po rezervaciji u krugu: brava na redu (findByIdForUpdate) dok se mejl
     * šalje i „poslato" upisuje. Ručno slanje iz panela drži istu bravu, pa se ne mogu preklopiti -
     * ko god uđe drugi, zatekne „već poslato". Petlja i dalje NIJE @Transactional (detached
     * entiteti, ciljani upisi), transakcija je samo oko slanja jedne rezervacije.
     */
    private org.springframework.transaction.support.TransactionTemplate tx() {
        return new org.springframework.transaction.support.TransactionTemplate(txManager);
    }

    @Value("${app.cors-allowed-origin:https://escapii.rs}")
    private String corsAllowedOrigin;

    @Value("${app.cors-extra-origins:}")
    private String corsExtraOrigins;

    @Value("${app.frontend-url:https://escapii.rs}")
    private String defaultFrontendUrl;

    // NAPOMENA: bez @Transactional na nivou metode - svaki booking se čuva u
    // sopstvenoj transakciji (save = zaseban commit). Tako pad obrade jednog
    // bookinga ne poništava "sentAt" flag onih kojima je mejl već uspešno poslat
    // (sprečava dvostruko slanje pri retku grešku batch transakcije).
    @Override
    public void sendPendingReveals() {
        LocalDate today = LocalDate.now();
        List<Booking> readyList = bookingRepository.findReadyForReveal(today.plusDays(2));
        sendReveals(readyList);
    }

    @Override
    public void sendPendingForecasts() {
        LocalDate today = LocalDate.now();
        // Donja granica je danas, ne T-4: prognoza se pokušava svakog dana dok
        // polazak ne prođe, pa je ni kasno potvrđena rezervacija ne propusti.
        // Redosled u odnosu na reveal drži DailyTaskScheduler.
        List<Booking> readyList = bookingRepository.findReadyForForecast(
                today, today.plusDays(7));
        sendForecasts(readyList);
    }

    @Override
    @Transactional
    public void cancelStalePendingBookings() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(5);
        List<Booking> stale = bookingRepository.findStalePendingBefore(cutoff);

        if (stale.isEmpty()) {
            return;
        }
        for (Booking b : stale) {
            b.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(b);
            log.info("[Scheduler] Auto-cancel: {} otkazan (kreiran: {})", b.getBookingRef(), b.getCreatedAt());

            // Oslobodi vaučer ako je postojao - reversiraj usedAmount i postavi ACTIVE
            // findByCodeForUpdate (ne findByCode) - zaključava red protiv race-a sa
            // istovremenim bookingom koji koristi isti kod.
            if (b.getAppliedVoucherCode() != null) {
                giftVoucherRepository.findByCodeForUpdate(b.getAppliedVoucherCode()).ifPresent(v -> {
                    if (v.getStatus() == VoucherStatus.RESERVED || v.getStatus() == VoucherStatus.ACTIVE) {
                        // Isti ledger kao rucno otkazivanje i brisanje - oslobadja tacno
                        // ono sto je rezervacija drzala. Ranije je ovde bila treca kopija
                        // iste racunice, i bas su se te kopije vremenom razisle.
                        java.math.BigDecimal oslobodjeno = voucherLedger.release(b, v);
                        if (oslobodjeno.signum() == 0) return;
                        giftVoucherRepository.save(v);
                        log.info("[Voucher] {} → {} (auto-cancel booking {}, oslobođeno {}€, novo usedAmount={}€)",
                                com.escapii.util.LogUtils.maskVoucherCode(v.getCode()), v.getStatus(),
                                b.getBookingRef(), oslobodjeno, v.getUsedAmount());
                    }
                });
            }
        }
        log.info("[Scheduler] Auto-cancel završen - otkazano {} rezervacija.", stale.size());
    }

    @Override
    @Transactional
    public void completeFinishedBookings() {
        LocalDate today = LocalDate.now();
        List<Booking> ready = bookingRepository.findReadyForCompletion(today);

        if (ready.isEmpty()) {
            return;
        }
        for (Booking b : ready) {
            b.setStatus(BookingStatus.COMPLETED);
            bookingRepository.save(b);

            // Trajno označi vaučer kao iskorišćen - putovanje završeno
            if (b.getAppliedVoucherCode() != null) {
                giftVoucherRepository.findByCodeForUpdate(b.getAppliedVoucherCode()).ifPresent(v -> {
                    if (v.getStatus() == VoucherStatus.RESERVED) {
                        v.setStatus(VoucherStatus.USED);
                        v.setUsedAt(java.time.LocalDateTime.now());
                        giftVoucherRepository.save(v);
                        log.info("[Voucher] {} → USED (auto-complete booking {})", com.escapii.util.LogUtils.maskVoucherCode(v.getCode()), b.getBookingRef());
                    }
                });
            }
        }
        log.info("[Scheduler] Auto-complete završen - zatvoreno {} rezervacija.", ready.size());
    }

    @Override
    @Transactional
    public Map<String, String> sendRevealForBooking(Long bookingId, String siteUrl) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking nije pronađen."));

        if (booking.getRevealSentAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Reveal je već poslan " + booking.getRevealSentAt() + ".");
        }
        validateIsAssignedDestination(booking.getAssignedDestination());


        // Validiraj X-Frontend-Url da nije open redirect - mora biti u dozvoljenim originima
        String validatedUrl = validateFrontendUrl(siteUrl);

        if (booking.getRevealToken() == null) {
            booking.setRevealToken(TokenUtils.generate());
        }

        // Šalji PRE upisa revealSentAt - isti obrazac kao automatski sendReveals()
        // i InvoiceServiceImpl.sendInvoice(). sendRevealEmail baca bare RuntimeException
        // na neuspeh (ne boolean) - hvatamo je ovde da admin dobije čist 502 umesto
        // generičkog 500 koji nepotrebno spamuje AppError alert za obično privremeni
        // SMTP hiccup, i da revealSentAt nikad ne bude upisan pre potvrđenog uspeha.
        try {
            revealEmailService.sendRevealEmail(booking, validatedUrl);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Slanje reveal emaila nije uspelo. Reveal nije evidentiran - pokušaj ponovo.");
        }

        booking.setRevealSentAt(LocalDateTime.now());
        bookingRepository.save(booking);

        log.info("[Admin] Ručni reveal poslan za {} → '{}' (siteUrl={})",
                booking.getBookingRef(), booking.getAssignedDestination(), validatedUrl);

        // Isti auto-send hook kao u sendReveals() - za box korisnika dokument moze
        // odmah da ide (revealSentAt je postavljen), za non-box ceka klik pa retry.
        try {
            confirmationDocumentAutoSender.sendIfReadyAndPending(booking);
        } catch (Exception ex) {
            log.warn("[Admin] Auto-send dokumenta za {} pao, ostaje za retry: {}",
                    booking.getBookingRef(), ex.getMessage());
        }
        return Map.of("message", "Reveal email poslan za " + booking.getBookingRef() + ".");
    }

    @Override
    @Transactional
    public Map<String, String> sendForecastForBooking(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking nije pronađen."));

        if (booking.getForecastSentAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Prognoza je već poslata " + booking.getForecastSentAt() + ".");
        }
        validateIsAssignedDestination(booking.getAssignedDestination());

        String weatherQuery = resolveWeatherQuery(booking);
        Optional<List<DailyForecast>> forecast = weatherService.getForecast(weatherQuery);
        if (forecast.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Nije moguće preuzeti prognozu za '" + weatherQuery + "'. " +
                    "Pokušaj uneti precizniji naziv u polje 'Grad za prognozu'.");
        }
        // Prognoza ide najviše PROGNOZA_DANA_UNAPRED dana unapred. Poslata ranije, mejl bi imao samo
        // „Trenutno vreme" i dane puta bez podataka, a upisano „poslato" bi ugasilo automatsku
        // prognozu na T-7 (traži forecastSentAt = null). Zato se odbija, bez upisa.
        proveriDaPrognozaPokrivaPolazak(booking, forecast.get());

        // Šalji PRE upisa forecastSentAt - isti obrazac kao automatski sendForecasts()
        // i InvoiceServiceImpl.sendInvoice(). sendForecastEmail baca bare RuntimeException
        // na neuspeh (ne boolean) - hvatamo je ovde da admin dobije čist 502 umesto
        // generičkog 500 koji nepotrebno spamuje AppError alert za obično privremeni
        // SMTP hiccup, i da forecastSentAt nikad ne bude upisan pre potvrđenog uspeha.
        try {
            forecastEmailService.sendForecastEmail(booking, forecast.get());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Slanje prognoze nije uspelo. Prognoza nije evidentirana - pokušaj ponovo.");
        }

        booking.setForecastSentAt(LocalDateTime.now());
        bookingRepository.save(booking);

        log.info("[Admin] Ručna prognoza poslana za {} → '{}' (weatherQuery='{}')",
                booking.getBookingRef(), booking.getAssignedDestination(), weatherQuery);
        return Map.of("message", "Prognoza email poslan za " + booking.getBookingRef() + ".");
    }

    // ── Weather helpers ───────────────────────────────────────────────────────

    /**
     * Vraća string koji se šalje geocoderu za vremensku prognozu.
     * Admin može uneti precizniji naziv u weatherCity polje (npr. "Santa Cruz de Tenerife, Spain")
     * dok assignedDestination ostaje marketinški naziv koji vidi korisnik (npr. "Tenerife").
     */
    private String resolveWeatherQuery(Booking booking) {
        String wc = booking.getWeatherCity();
        return (wc != null && !wc.isBlank()) ? wc.strip() : booking.getAssignedDestination();
    }

    // ── Security helpers ──────────────────────────────────────────────────────

    /**
     * Validira X-Frontend-Url header da sprečava open redirect napad.
     * Prihvata URL samo ako tačno odgovara jednom od dozvoljenih CORS origina.
     * Ako ne odgovara → vraća konfigurisani defaultFrontendUrl.
     */
    private String validateFrontendUrl(String siteUrl) {
        if (siteUrl == null || siteUrl.isBlank()) {
            return null; // RevealEmailServiceImpl će koristiti konfigurisani frontendUrl
        }

        Set<String> allowed = buildAllowedOrigins();
        String normalized = siteUrl.stripTrailing().replaceAll("/+$", "");

        if (allowed.contains(normalized)) {
            return normalized;
        }

        log.warn("[Security] X-Frontend-Url '{}' nije u dozvoljenim originima - koristi se default", siteUrl);
        return defaultFrontendUrl;
    }

    private Set<String> buildAllowedOrigins() {
        Set<String> origins = new java.util.HashSet<>();
        if (corsAllowedOrigin != null && !corsAllowedOrigin.isBlank()) {
            Arrays.stream(corsAllowedOrigin.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(origins::add);
        }
        if (corsExtraOrigins != null && !corsExtraOrigins.isBlank()) {
            Arrays.stream(corsExtraOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(origins::add);
        }
        return origins;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<Booking> sendForecasts(List<Booking> readyList) {
        List<Booking> sent = new ArrayList<>();

        for (Booking booking : readyList) {
            try {
                String weatherQuery = resolveWeatherQuery(booking);
                Optional<List<DailyForecast>> forecast = weatherService.getForecast(weatherQuery);

                if (forecast.isEmpty()) {
                    log.warn("[Forecast] Nije moguće preuzeti prognozu za '{}' weatherQuery='{}' ({})",
                            booking.getAssignedDestination(), weatherQuery, booking.getBookingRef());
                    prijaviNedostupnuPrognozu(booking, weatherQuery);
                    continue;
                }

                // Slanje i upis „poslato" pod bravom na redu rezervacije (vremenski API je već
                // pozvan, van brave). Ručno slanje iz panela (findByIdForUpdate) čeka da brava
                // padne i onda vidi forecastSentAt - dupli mejl nije moguć ni kad admin klikne
                // dok krug šalje.
                List<DailyForecast> prognoza = forecast.get();
                if (!pokrivaPolazak(prognoza, booking)) {
                    // Na T-7 ne bi trebalo da se desi (domet je 16 dana) - jedino kad servis vrati
                    // kraći niz. Ne upisuje se „poslato": sutra se pokušava ponovo.
                    log.warn("[Forecast] Prognoza za '{}' ne doseže dan polaska ({}{}) - preskačem, ide sutra",
                            weatherQuery, booking.getBookingRef(), opisPolaska(booking));
                    prijaviNedostupnuPrognozu(booking, weatherQuery);
                    continue;
                }
                Boolean poslato = tx().execute(status -> {
                    bookingRepository.findByIdForUpdate(booking.getId());
                    // Stanje iz baze, ne iz snimka od početka petlje: admin je mogao ručno
                    // poslati, skloniti destinaciju ili otkazati otkad je lista učitana.
                    if (bookingRepository.jeLiJosZaPrognozu(booking.getId()) == 0) {
                        log.info("[Forecast] {} preskočen - stanje se promenilo otkad je lista učitana",
                                booking.getBookingRef());
                        return false;
                    }
                    forecastEmailService.sendForecastEmail(booking, prognoza);
                    // Ciljani upis umesto save(): booking je DETACHED (lista je učitana ranije),
                    // save() bi bio merge i prepisao bi sve kolone starim snimkom (PDF, beleške,
                    // otkaz). Videti BookingRepository.markForecastSent.
                    LocalDateTime sada = LocalDateTime.now();
                    bookingRepository.markForecastSent(booking.getId(), sada);
                    booking.setForecastSentAt(sada);   // samo da jutarnji digest prikaže tačno
                    return true;
                });
                if (!Boolean.TRUE.equals(poslato)) continue;
                sent.add(booking);
                log.info("[Forecast] {} → dest='{}' dana={}",
                        booking.getBookingRef(),
                        booking.getAssignedDestination(),
                        prognoza.size());
            } catch (Exception e) {
                log.error("[Forecast] Greška za {}: {}", booking.getBookingRef(), e.getMessage(), e);
                prijaviPad(GRESKA_PROGNOZA, booking, "Prognoza nije poslata", e);
            }
        }

        if (!sent.isEmpty()) {
            log.info("[Forecast] Ukupno poslato: {}/{}", sent.size(), readyList.size());
        }
        return sent;
    }

    private List<Booking> sendReveals(List<Booking> readyList) {
        List<Booking> sent = new ArrayList<>();

        for (Booking booking : readyList) {
            try {
                // Reveal ide SVIMA, i onima sa Reveal Boxom - kutija je fizicki
                // dodatak i prati se odvojeno preko revealBoxSent/markRevealBoxSent.

                // Reveal NIKAD pre prognoze - to je suština proizvoda. Redosled
                // poziva (prognoza pa reveal) nije dovoljan: ako prognoza tiho
                // padne (npr. weather API vrati 503, kao 23.07), reveal bi svejedno
                // otišao. Zato eksplicitno čekamo forecastSentAt. Ako prognoza baš
                // zaglavi, reveal čeka sledeći ciklus; digest to pokazuje, a admin
                // može ručno poslati reveal (sendRevealForBooking to namerno ne proverava).
                if (booking.getForecastSentAt() == null) {
                    log.warn("[Reveal] {} preskočen - prognoza još nije poslata, reveal čeka", booking.getBookingRef());
                    continue;
                }

                // Slanje i upis pod bravom na redu rezervacije - isto kao kod prognoze: ručno
                // slanje iz panela (findByIdForUpdate) i krug se ne mogu preklopiti.
                Boolean poslato = tx().execute(status -> {
                    bookingRepository.findByIdForUpdate(booking.getId());
                    // Odluka o slanju se donosi na stanju iz BAZE, ne na snimku od početka
                    // petlje: admin je mogao ručno poslati reveal, skloniti destinaciju ili
                    // otkazati rezervaciju - bez ove provere bi kupac dobio drugi reveal mejl.
                    if (bookingRepository.jeLiJosZaReveal(booking.getId()) == 0) {
                        log.info("[Reveal] {} preskočen - stanje se promenilo otkad je lista učitana",
                                booking.getBookingRef());
                        return false;
                    }
                    if (booking.getRevealToken() == null) {
                        // Token mora biti u bazi pre nego što korisnik klikne link. Upisuje
                        // se ciljano (i samo ako ga još nema) umesto saveAndFlush, koji bi
                        // kao merge detached entiteta pregazio ostale kolone.
                        String noviToken = TokenUtils.generate();
                        bookingRepository.saveRevealTokenIfAbsent(booking.getId(), noviToken);
                        // Ako je token u međuvremenu upisao neko drugi (ručno slanje iz
                        // panela), mejl mora nositi TAJ token, ne naš - inače bi link u
                        // mejlu bio mrtav.
                        booking.setRevealToken(
                                bookingRepository.findRevealTokenById(booking.getId()).orElse(noviToken));
                    }
                    revealEmailService.sendRevealEmail(booking);
                    // Ciljani upis - isti razlog kao kod prognoze iznad.
                    LocalDateTime sada = LocalDateTime.now();
                    bookingRepository.markRevealSent(booking.getId(), sada);
                    booking.setRevealSentAt(sada);
                    return true;
                });
                if (!Boolean.TRUE.equals(poslato)) continue;
                sent.add(booking);
                log.info("[Reveal] {} → {}", booking.getBookingRef(), booking.getAssignedDestination());

                // Auto-send dokumenta ako je uslov ispunjen. Za box korisnika ovo znaci
                // odmah nakon reveala (revealSentAt je maloprije postavljen), za non-box
                // tek kad kupac klikne link - to ide kroz DailyTaskScheduler retry, ne ovde.
                // Pad je bez posledice: sendPendingConfirmationDocuments ce ga pokupiti sutra.
                try {
                    confirmationDocumentAutoSender.sendIfReadyAndPending(booking);
                } catch (Exception ex) {
                    log.warn("[Reveal] Auto-send dokumenta za {} pao, ostaje za retry: {}",
                            booking.getBookingRef(), ex.getMessage());
                }
            } catch (Exception e) {
                log.error("[Reveal] Greška za {}: {}", booking.getBookingRef(), e.getMessage(), e);
                prijaviPad(GRESKA_REVEAL, booking, "Reveal nije poslat", e);
            }
        }

        if (!sent.isEmpty()) {
            log.info("[Reveal] Ukupno poslato: {}/{}", sent.size(), readyList.size());
        }
        return sent;
    }

    // ── Prijava padova jutarnjeg kruga ────────────────────────────────────────

    /** Mesto greške u tabu Greške - po jedan red za svaku vrstu pada, ponavljanja se broje. */
    static final String GRESKA_PROGNOZA            = "Jutarnji krug: prognoza";
    static final String GRESKA_PROGNOZA_NEDOSTUPNA = "Jutarnji krug: prognoza nedostupna";
    static final String GRESKA_REVEAL              = "Jutarnji krug: reveal";

    /**
     * Nedostupna prognoza je obična pojava (weather API ume da zakaže, sutra se pokušava
     * ponovo), pa postaje greška tek kad ugrozi reveal: polazak za ovoliko dana ili manje.
     */
    static final int PROGNOZA_HITNO_DANA = 3;

    /** Domet vremenskog servisa (Open-Meteo, forecast_days=16): danas + 15 dana. */
    static final int PROGNOZA_DANA_UNAPRED = 16;

    private static final java.time.format.DateTimeFormatter POLAZAK_FMT =
            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy.");

    /**
     * Omotač za padove jutarnjeg kruga. Pad je ranije ostajao samo u logu: server radi,
     * health je zelen, a kupac ne dobije reveal. Poruka nosi šifru rezervacije i datum
     * polaska da se iz mejla odmah vidi koga treba ručno obraditi; uzrok ostaje u
     * stack trace-u.
     */
    static final class JutarnjiKrugGreska extends RuntimeException {
        JutarnjiKrugGreska(String poruka, Throwable uzrok) {
            super(poruka, uzrok);
        }
    }

    private void prijaviPad(String gde, Booking booking, String sta, Exception uzrok) {
        zabelezi(gde, new JutarnjiKrugGreska(
                sta + " za " + booking.getBookingRef() + opisPolaska(booking) + " - " + uzrok, uzrok));
    }

    private void prijaviNedostupnuPrognozu(Booking booking, String weatherQuery) {
        LocalDate polazak = polazak(booking);
        if (polazak == null || polazak.isAfter(LocalDate.now().plusDays(PROGNOZA_HITNO_DANA))) {
            return;
        }
        zabelezi(GRESKA_PROGNOZA_NEDOSTUPNA, new JutarnjiKrugGreska(
                "Prognoza nije dostupna za '" + weatherQuery + "' (" + booking.getBookingRef() + opisPolaska(booking)
                + ") - reveal čeka dok prognoza ne ode. Unesi precizniji 'Grad za prognozu' ili pošalji ručno iz panela.",
                null));
    }

    private static LocalDate polazak(Booking booking) {
        return booking.getSelectedDate() != null ? booking.getSelectedDate().getDepartureDate() : null;
    }

    /**
     * Da li prognoza sadrži dan polaska. Bez tog dana nema šta da se pošalje: krupna kartica i
     * savet za pakovanje se grade iz dana puta (ForecastEmailServiceImpl), a servis daje najviše
     * {@link #PROGNOZA_DANA_UNAPRED} dana unapred.
     */
    static boolean pokrivaPolazak(List<DailyForecast> prognoza, Booking booking) {
        LocalDate polazak = polazak(booking);
        return polazak != null && prognoza.stream().anyMatch(d -> polazak.equals(d.date()));
    }

    /** Ručno slanje iz panela: 409 sa objašnjenjem kad je polazak van dometa prognoze. */
    private static void proveriDaPrognozaPokrivaPolazak(Booking booking, List<DailyForecast> prognoza) {
        if (pokrivaPolazak(prognoza, booking)) return;
        LocalDate polazak = polazak(booking);
        String kad = "";
        if (polazak != null) {
            long zaDana = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), polazak);
            kad = " (" + polazak.format(POLAZAK_FMT) + ", za " + zaDana + " dana)";
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Prognoza još ne pokriva dan polaska" + kad + " - servis je daje najviše "
                + PROGNOZA_DANA_UNAPRED + " dana unapred. Automatski se šalje 7 dana pre polaska"
                + (polazak != null ? ", ručno je moguće od " + polazak.minusDays(PROGNOZA_DANA_UNAPRED - 1).format(POLAZAK_FMT) : "")
                + ". Ništa nije poslato ni evidentirano.");
    }

    private static String opisPolaska(Booking booking) {
        LocalDate polazak = polazak(booking);
        return polazak != null ? ", polazak " + polazak.format(POLAZAK_FMT) : "";
    }

    /** Beleženje greške nikad ne sme da prekine petlju - ostale rezervacije idu dalje. */
    private void zabelezi(String gde, Exception greska) {
        try {
            appErrorService.record(gde, 0, greska);
        } catch (Exception ex) {
            log.warn("[Scheduler] AppError nije zabeležen ({}): {}", gde, ex.toString());
        }
    }

    private void validateIsAssignedDestination(String assignedDestination) {
        if (assignedDestination == null || assignedDestination.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Destinacija nije unesena - unesi je pre slanja prognoze.");
        }
    }
}
