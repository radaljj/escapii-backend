package com.escapii.service.impl;

import com.escapii.model.Booking;
import com.escapii.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Da li je jutarnji krug uradio svoj posao - za /api/health/jobs, koji prati spoljni bot.
 *
 * <p>Ne pamti ništa u memoriji: stanje se čita iz baze svaki put. Zato radi i posle
 * restarta, i hvata i slučaj kad krug u 10:00 uopšte nije pokrenut (server je bio dole
 * ili se baš tad deployovao) - tada reveal ostane neposlat i ovo ga vidi.
 *
 * <p>Pravilo: krug u 10:00 šalje reveal za polaske do danas+2. Posle roka
 * ({@link #ROK_JUTARNJEG_KRUGA}) svaka potvrđena rezervacija sa polaskom od danas do
 * danas+2 bez reveala kasni. Pre roka važi jučerašnji krug, dakle polasci do danas+1.
 */
@Service
@RequiredArgsConstructor
public class JobHealthService {

    /** Krug kreće u 10:00 (DailyTaskScheduler); pola sata rezerve za prognoze i SMTP. */
    static final LocalTime ROK_JUTARNJEG_KRUGA = LocalTime.of(10, 30);

    private final BookingRepository bookingRepository;

    /**
     * @param revealKasni        ukupno rezervacija koje čekaju reveal posle roka
     * @param bezDestinacije     od toga: destinacija nije uneta
     * @param cekaPrognozu       od toga: destinacija uneta, prognoza nije otišla (reveal nikad ne ide pre nje)
     * @param slanjeNijeUspelo   od toga: sve spremno, a reveal mejl nije otišao
     * @param najranijiPolazak   najbliži polazak među njima, null kad nema nijedne
     * @param proveravaPolaskeDo do kog datuma polaska je provera gledala
     */
    public record Stanje(int revealKasni, int bezDestinacije, int cekaPrognozu, int slanjeNijeUspelo,
                         LocalDate najranijiPolazak, LocalDate proveravaPolaskeDo) {
        public boolean ok() {
            return revealKasni == 0;
        }
    }

    @Transactional(readOnly = true)
    public Stanje proveri(LocalDateTime sada) {
        LocalDate danas = sada.toLocalDate();
        LocalDate doPolaska = sada.toLocalTime().isBefore(ROK_JUTARNJEG_KRUGA)
                ? danas.plusDays(1)
                : danas.plusDays(2);

        List<Booking> kasne = bookingRepository.findRevealOverdue(danas, doPolaska);

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
            LocalDate polazak = b.getSelectedDate() != null ? b.getSelectedDate().getDepartureDate() : null;
            if (polazak != null && (najraniji == null || polazak.isBefore(najraniji))) {
                najraniji = polazak;
            }
        }
        return new Stanje(kasne.size(), bezDestinacije, cekaPrognozu, slanjeNijeUspelo, najraniji, doPolaska);
    }
}
