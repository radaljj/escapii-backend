package com.escapii.service.impl;

import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Rok jutarnjeg kruga i razvrstavanje razloga zašto reveal kasni. */
@SuppressWarnings("unchecked")
class JobHealthServiceTest {

    private static final LocalDate DANAS = LocalDate.of(2026, 9, 14);

    private final BookingRepository repo = mock(BookingRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JobHealthService svc = new JobHealthService(repo, jdbc);

    @BeforeEach
    void dnevniKrugPreuzetDanas() {
        dnevniKrug(DANAS);
    }

    /** scheduler_runs.last_run_date za "daily"; null = nema reda. */
    private void dnevniKrug(LocalDate datum) {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(datum == null ? List.of() : List.of(java.sql.Date.valueOf(datum)));
    }

    @Test
    void dnevniKrugNijePreuzetPosle11_kasni() {
        dnevniKrug(DANAS.minusDays(1));

        JobHealthService.Stanje s = svc.proveri(DANAS.atTime(11, 0));

        assertFalse(s.ok());
        assertTrue(s.dnevniKrugKasni());
        assertEquals("DNEVNI_KRUG_KASNI", s.status());
    }

    @Test
    void dnevniKrugOdJuce_pre11_jeUredu_aBezTabeleKasni() {
        dnevniKrug(DANAS.minusDays(1));
        assertTrue(svc.proveri(DANAS.atTime(10, 59)).ok(), "pre 11h juceranji datum je normalan");

        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenThrow(new RuntimeException("nema tabele"));
        JobHealthService.Stanje s = svc.proveri(DANAS.atTime(12, 0));
        assertTrue(s.dnevniKrugKasni(), "bez tabele = nije preuzet, ne DOWN");
        assertEquals("DNEVNI_KRUG_KASNI", s.status());
    }

    @Test
    void revealKasniImaPrednostNadDnevnimKrugom() {
        dnevniKrug(null);
        when(repo.findRevealOverdue(DANAS, DANAS.plusDays(2))).thenReturn(List.of(b(DANAS, null, null)));

        JobHealthService.Stanje s = svc.proveri(DANAS.atTime(15, 0));

        assertEquals("REVEAL_KASNI", s.status());
        assertTrue(s.dnevniKrugKasni());
    }

    private static Booking b(LocalDate polazak, String destinacija, LocalDateTime prognoza) {
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(polazak);
        Booking b = new Booking();
        b.setSelectedDate(d);
        b.setAssignedDestination(destinacija);
        b.setForecastSentAt(prognoza);
        return b;
    }

    @Test
    void preRokaVaziJucerasnjiKrug_polasciDoSutra() {
        when(repo.findRevealOverdue(DANAS, DANAS.plusDays(1))).thenReturn(List.of());

        JobHealthService.Stanje s = svc.proveri(DANAS.atTime(10, 29));

        assertTrue(s.ok());
        assertEquals(DANAS.plusDays(1), s.proveravaPolaskeDo());
        verify(repo).findRevealOverdue(DANAS, DANAS.plusDays(1));
    }

    @Test
    void odRokaVaziDanasnjiKrug_polasciDoPrekosutra() {
        when(repo.findRevealOverdue(DANAS, DANAS.plusDays(2))).thenReturn(List.of());

        JobHealthService.Stanje s = svc.proveri(DANAS.atTime(10, 30));

        assertTrue(s.ok());
        assertEquals(DANAS.plusDays(2), s.proveravaPolaskeDo());
    }

    @Test
    void razlogKasnjenjaSeRazvrstava() {
        when(repo.findRevealOverdue(DANAS, DANAS.plusDays(2))).thenReturn(List.of(
                b(DANAS.plusDays(2), null, null),
                b(DANAS.plusDays(1), "  ", null),
                b(DANAS.plusDays(2), "Prag", null),
                b(DANAS, "Beč", DANAS.minusDays(7).atTime(10, 0))));

        JobHealthService.Stanje s = svc.proveri(DANAS.atTime(15, 0));

        assertFalse(s.ok());
        assertEquals(4, s.revealKasni());
        assertEquals(2, s.bezDestinacije());
        assertEquals(1, s.cekaPrognozu());
        assertEquals(1, s.slanjeNijeUspelo());
        assertEquals(DANAS, s.najranijiPolazak());
    }

    @Test
    void prognozaKasni_kadProdjeDanBezNje_aRevealJosNe() {
        // polazak za 5 dana, prognoza je trebalo da ode prekjuce (T-7) - kasni; reveal jos nije na redu
        when(repo.findForecastOverdue(DANAS, DANAS.plusDays(5))).thenReturn(List.of(
                b(DANAS.plusDays(5), "Prag", null)));

        JobHealthService.Stanje s = svc.proveri(DANAS.atTime(15, 0));

        assertFalse(s.ok());
        assertEquals("PROGNOZA_KASNI", s.status());
        assertEquals(1, s.prognozaKasni());
        assertEquals(0, s.revealKasni());
        assertEquals(DANAS.plusDays(5), s.najranijiPolazak());
    }

    @Test
    void prognozaZaPolazakZa6Ili7Dana_nijeKasnjenje() {
        // rezervacija potvrdjena uvece, posle poslednjeg kruga, za polazak za 6-7 dana: prognoza
        // ide sutra u 10:00 - health ne sme preko noci da drzi alarm (gleda se do danas+5)
        when(repo.findForecastOverdue(DANAS, DANAS.plusDays(5))).thenReturn(List.of());

        JobHealthService.Stanje s = svc.proveri(DANAS.atTime(15, 0));

        assertTrue(s.ok());
        assertEquals("OK", s.status());
        verify(repo).findForecastOverdue(DANAS, DANAS.plusDays(5));
    }

    @Test
    void preRokaPrognozaSeGledaDoDanasPlus4() {
        svc.proveri(DANAS.atTime(9, 0));
        verify(repo).findForecastOverdue(DANAS, DANAS.plusDays(4));
    }
}
