package com.escapii.service.impl;

import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.TermDestinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Brisanje prošlih termina bez rezervacija, zajedno sa svim što na njih pokazuje.
 *
 * <p>Termin se briše masovnim DELETE-om, a masovni DELETE zaobilazi JPA kaskadu:
 * {@code cascade}/{@code orphanRemoval} na {@code termDestinations} rade samo kad se briše
 * entitet (kao ručno brisanje u panelu). U bazi {@code term_destination} drži FK ka terminu
 * bez ON DELETE CASCADE, pa je jutarnje brisanje padalo čim bi istekao termin sa
 * destinacijama - a to je svaki termin napravljen iz panela.
 *
 * <p>Zato se u JEDNOJ transakciji, istim uslovom, prvo brišu veze pa termini. Ako bilo šta
 * pukne, ništa se ne briše, a greška ide u AppError kroz jutarnji krug.
 */
@Service
@RequiredArgsConstructor
public class ExpiredDateCleanup {

    /**
     * Join tabela iz vremena kad su destinacije termina bile @ManyToMany. Zamenila ju je
     * term_destination i više nije mapirana, ali Hibernate "update" ne briše tabele: u bazama
     * nastalim pre te promene i dalje postoji, sa redovima starih termina i FK-om ka terminu.
     */
    static final String STARA_TABELA_VEZA = "available_date_destinations";

    private final AvailableDateRepository   availableDateRepository;
    private final TermDestinationRepository termDestinationRepository;
    private final JdbcTemplate              jdbc;

    /** Koliko je obrisano: termina, njihovih veza ka destinacijama, i redova iz stare tabele veza. */
    public record Rezultat(int termina, int veza, int starihVeza) {}

    @Transactional
    public Rezultat obrisiIstekleBezRezervacija(LocalDate cutoff) {
        int veza = termDestinationRepository.deleteForExpiredDatesWithNoBookings(cutoff);
        int starihVeza = postojiStaraTabelaVeza()
                ? jdbc.update("DELETE FROM " + STARA_TABELA_VEZA + " WHERE available_date_id IN ("
                        + "SELECT d.id FROM available_dates d WHERE d.departure_date < ? "
                        + "AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.selected_date_id = d.id))", cutoff)
                : 0;
        int termina = availableDateRepository.deleteExpiredWithNoBookings(cutoff);
        return new Rezultat(termina, veza, starihVeza);
    }

    /**
     * Proverava kolonu, ne samo tabelu: u Postgresu greška u jednom upitu obori celu
     * transakciju, pa DELETE ne sme ni da se pokuša nad nečim što ne postoji.
     */
    boolean postojiStaraTabelaVeza() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = ? AND column_name = 'available_date_id'",
                Integer.class, STARA_TABELA_VEZA);
        return n != null && n > 0;
    }
}
