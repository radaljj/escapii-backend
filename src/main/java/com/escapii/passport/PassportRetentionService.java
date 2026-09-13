package com.escapii.passport;

import com.escapii.model.Booking;
import com.escapii.model.PassengerInfo;
import com.escapii.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Broj pasoša se čuva samo dok treba agenciji: briše se {@code retention-days-after-return}
 * dana posle povratka (bilo koji status), a kod otkazane rezervacije čim prođe datum polaska
 * (posle toga otkaz više ne može da se poništi). Ime, datum rođenja, zemlja pasoša i oznaka
 * „validan" ostaju - to su podaci o rezervaciji, ne identifikacioni dokument.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PassportRetentionService {

    private final BookingRepository bookingRepository;

    @Value("${app.passport.retention-days-after-return:30}")
    private int daysAfterReturn;

    /** Briše brojeve pasoša kojima je istekao rok. Vraća broj obrisanih brojeva. */
    @Transactional
    public int purgeExpired(LocalDate today) {
        LocalDate cutoff = today.minusDays(daysAfterReturn);
        List<Booking> kandidati = bookingRepository.findWithPassportsToPurge(cutoff, today);
        int obrisano = 0;
        for (Booking b : kandidati) {
            for (PassengerInfo p : b.getPassengers()) {
                if (p.getPassportNumber() != null) {
                    p.setPassportNumber(null);
                    obrisano++;
                }
            }
        }
        if (!kandidati.isEmpty()) {
            bookingRepository.saveAll(kandidati);
        }
        log.info("[Pasoši] Obrisano {} brojeva pasoša u {} rezervacija (povratak pre {}, ili otkazane sa prošlim polaskom)",
                obrisano, kandidati.size(), cutoff);
        return obrisano;
    }

    public int daysAfterReturn() { return daysAfterReturn; }
}
