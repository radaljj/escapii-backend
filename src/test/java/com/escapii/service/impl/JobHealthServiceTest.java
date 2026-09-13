package com.escapii.service.impl;

import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.repository.BookingRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Rok jutarnjeg kruga i razvrstavanje razloga zašto reveal kasni. */
class JobHealthServiceTest {

    private static final LocalDate DANAS = LocalDate.of(2026, 9, 14);

    private final BookingRepository repo = mock(BookingRepository.class);
    private final JobHealthService svc = new JobHealthService(repo);

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
}
