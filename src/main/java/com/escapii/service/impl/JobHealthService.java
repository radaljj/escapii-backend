package com.escapii.service.impl;

import com.escapii.config.DailyTaskScheduler;
import com.escapii.model.Booking;
import com.escapii.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Da li je jutarnji krug uradio svoj posao - za /api/health/jobs, koji prati spoljni bot.
 *
 * <p>Ne pamti ništa u memoriji: stanje se čita iz baze svaki put. Zato radi i posle
 * restarta, i hvata i slučaj kad krug uopšte nije pokrenut (server je bio dole) - tada
 * reveal ili prognoza ostanu neposlati i ovo ih vidi.
 *
 * <p>Pravila (prvi krug je u 10:00, pa se ponavlja na svakih 30 min do 21:30):
 * <ul>
 *   <li><b>Reveal</b> ide za polaske do danas+2. Posle roka ({@link #ROK_JUTARNJEG_KRUGA})
 *       svaka potvrđena rezervacija sa polaskom od danas do danas+2 bez reveala kasni; pre
 *       roka važi jučerašnji krug, dakle polasci do danas+1.</li>
 *   <li><b>Prognoza</b> ide za polaske do danas+7. Računa se kao zakasnela tek kad prođu dva
 *       dana bez nje (polazak do danas+5 posle roka, do danas+4 pre roka): rezervacija potvrđena
 *       uveče, posle poslednjeg kruga, za polazak za 6-7 dana dobija prognozu sutra u 10:00 i
 *       ne sme preko noći da drži alarm. Reveal je na T-2, pa T-5 ostavlja dovoljno vremena.
 *       Rezervacije bez destinacije se ne broje (imaju svoje upozorenje, a na T-2 postaju
 *       reveal koji kasni), ni one kojima je reveal već otišao ručno.</li>
 *   <li><b>Dnevni krug</b> (završavanje, upozorenja, digest, čišćenje, pasoši) se prijavljuje u
 *       {@code scheduler_runs}; ako do {@link #ROK_DNEVNOG_KRUGA} nije prijavljen za danas,
 *       kasni - server je bio dole u 10:00 i 10:30, ili prijava u bazi ne prolazi.</li>
 * </ul>
 *
 * <p>Namerno bez @Transactional: svaki upit ide zasebno, pa pad čitanja {@code scheduler_runs}
 * (nema tabele) ne obara ostatak provere - bot i dalje dobija razloge, ne samo DOWN.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobHealthService {

    /** Krug kreće u 10:00 (DailyTaskScheduler); pola sata rezerve za prognoze i SMTP. */
    static final LocalTime ROK_JUTARNJEG_KRUGA = LocalTime.of(10, 30);

    /** Dnevni krug se prijavljuje na prvom krugu posle 10:00 koji server dočeka (10:00 ili 10:30). */
    static final LocalTime ROK_DNEVNOG_KRUGA = LocalTime.of(11, 0);

    /** Prognoza ide na T-7; kasni kad prođu dva dana bez nje (T-5). */
    static final int PROGNOZA_KASNI_OD_DANA = 5;

    private final BookingRepository bookingRepository;
    private final JdbcTemplate jdbc;

    /**
     * @param revealKasni        ukupno rezervacija koje čekaju reveal posle roka
     * @param prognozaKasni      rezervacija sa destinacijom i polaskom za 5 dana ili manje bez poslate prognoze
     * @param bezDestinacije     od reveal-kasni: destinacija nije uneta
     * @param cekaPrognozu       od reveal-kasni: destinacija uneta, prognoza nije otišla (reveal nikad ne ide pre nje)
     * @param slanjeNijeUspelo   od reveal-kasni: sve spremno, a reveal mejl nije otišao
     * @param najranijiPolazak   najbliži polazak među zakasnelim (reveal ili prognoza), null kad nema
     * @param proveravaPolaskeDo do kog datuma polaska je provera reveala gledala
     * @param dnevniKrugKasni    posle 11:00 dnevni krug još nije prijavljen za danas
     */
    public record Stanje(int revealKasni, int prognozaKasni, int bezDestinacije, int cekaPrognozu, int slanjeNijeUspelo,
                         LocalDate najranijiPolazak, LocalDate proveravaPolaskeDo, boolean dnevniKrugKasni) {
        public boolean ok() {
            return revealKasni == 0 && prognozaKasni == 0 && !dnevniKrugKasni;
        }

        /** Reveal je najozbiljniji, pa prognoza, pa dnevni krug - kad kasni više toga, status kaže najgore. */
        public String status() {
            if (revealKasni > 0)   return "REVEAL_KASNI";
            if (prognozaKasni > 0) return "PROGNOZA_KASNI";
            if (dnevniKrugKasni)   return "DNEVNI_KRUG_KASNI";
            return "OK";
        }
    }

    public Stanje proveri(LocalDateTime sada) {
        LocalDate danas = sada.toLocalDate();
        boolean preRoka = sada.toLocalTime().isBefore(ROK_JUTARNJEG_KRUGA);
        LocalDate doPolaska = preRoka ? danas.plusDays(1) : danas.plusDays(2);
        LocalDate prognozaDoPolaska = preRoka
                ? danas.plusDays(PROGNOZA_KASNI_OD_DANA - 1)
                : danas.plusDays(PROGNOZA_KASNI_OD_DANA);

        List<Booking> kasne = bookingRepository.findRevealOverdue(danas, doPolaska);
        List<Booking> bezPrognoze = bookingRepository.findForecastOverdue(danas, prognozaDoPolaska);
        boolean dnevniKasni = !sada.toLocalTime().isBefore(ROK_DNEVNOG_KRUGA) && !dnevniKrugPreuzet(danas);

        int bezDestinacije = 0, cekaPrognozu = 0, slanjeNijeUspelo = 0;
        LocalDate najraniji = null;
        for (Booking b : kasne) {
            if (b.getAssignedDestination() == null || b.getAssignedDestination().isBlank()) {
                bezDestinacije++;
            } else if (b.getForecastSentAt() == null) {
                cekaPrognozu++;
            } else {
                slanjeNijeUspelo++;
            }
            najraniji = raniji(najraniji, b);
        }
        for (Booking b : bezPrognoze) {
            najraniji = raniji(najraniji, b);
        }
        return new Stanje(kasne.size(), bezPrognoze.size(), bezDestinacije, cekaPrognozu, slanjeNijeUspelo,
                najraniji, doPolaska, dnevniKasni);
    }

    /** Red „daily" u scheduler_runs sa današnjim datumom. Bez reda, bez tabele ili bez baze: nije preuzet. */
    private boolean dnevniKrugPreuzet(LocalDate danas) {
        try {
            List<java.sql.Date> redovi = jdbc.query(
                    "SELECT last_run_date FROM scheduler_runs WHERE job = ?",
                    (rs, i) -> rs.getDate(1), DailyTaskScheduler.DNEVNI_POSAO);
            return !redovi.isEmpty() && redovi.get(0) != null && !redovi.get(0).toLocalDate().isBefore(danas);
        } catch (Exception e) {
            log.warn("[Health] scheduler_runs se ne može pročitati: {}", e.toString());
            return false;
        }
    }

    private static LocalDate raniji(LocalDate dosad, Booking b) {
        LocalDate polazak = b.getSelectedDate() != null ? b.getSelectedDate().getDepartureDate() : null;
        if (polazak == null) return dosad;
        return dosad == null || polazak.isBefore(dosad) ? polazak : dosad;
    }
}
