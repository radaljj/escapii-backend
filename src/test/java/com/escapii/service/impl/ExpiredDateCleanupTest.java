package com.escapii.service.impl;

import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.TermDestinationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Veze ka destinacijama moraju otići PRE termina (FK bez kaskade), istim uslovom, a stara
 * tabela veza samo ako postoji. Pravi FK i transakcija su provereni lokalno protiv Postgresa.
 */
class ExpiredDateCleanupTest {

    private static final LocalDate CUTOFF = LocalDate.of(2026, 9, 15);
    private static final String STARA_DELETE = "DELETE FROM available_date_destinations";

    private final AvailableDateRepository datumi = mock(AvailableDateRepository.class);
    private final TermDestinationRepository veze = mock(TermDestinationRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ExpiredDateCleanup cleanup = new ExpiredDateCleanup(datumi, veze, jdbc);

    private void staraTabela(int redovaUInformationSchema) {
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM information_schema.columns"),
                eq(Integer.class), eq("available_date_destinations"))).thenReturn(redovaUInformationSchema);
    }

    @Test
    void vezePaStareVezePaTermini_tacnoTimRedom() {
        staraTabela(1);
        when(veze.deleteForExpiredDatesWithNoBookings(CUTOFF)).thenReturn(5);
        when(jdbc.update(startsWith(STARA_DELETE), eq(CUTOFF))).thenReturn(2);
        when(datumi.deleteExpiredWithNoBookings(CUTOFF)).thenReturn(3);

        ExpiredDateCleanup.Rezultat r = cleanup.obrisiIstekleBezRezervacija(CUTOFF);

        assertEquals(new ExpiredDateCleanup.Rezultat(3, 5, 2), r);
        InOrder red = inOrder(veze, jdbc, datumi);
        red.verify(veze).deleteForExpiredDatesWithNoBookings(CUTOFF);
        red.verify(jdbc).update(startsWith(STARA_DELETE), eq(CUTOFF));
        red.verify(datumi).deleteExpiredWithNoBookings(CUTOFF);
    }

    @Test
    void staraTabelaSeBriseIstimUslovomKaoTermini() {
        staraTabela(1);

        cleanup.obrisiIstekleBezRezervacija(CUTOFF);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq(CUTOFF));
        assertTrue(sql.getValue().startsWith(STARA_DELETE), sql.getValue());
        assertTrue(sql.getValue().contains("d.departure_date < ?"), sql.getValue());
        assertTrue(sql.getValue().contains("NOT EXISTS (SELECT 1 FROM bookings b WHERE b.selected_date_id = d.id)"), sql.getValue());
    }

    @Test
    void bezStareTabeleNemaNjenogBrisanja() {
        staraTabela(0);
        when(datumi.deleteExpiredWithNoBookings(CUTOFF)).thenReturn(1);

        ExpiredDateCleanup.Rezultat r = cleanup.obrisiIstekleBezRezervacija(CUTOFF);

        assertEquals(new ExpiredDateCleanup.Rezultat(1, 0, 0), r);
        verify(jdbc).queryForObject(anyString(), eq(Integer.class), eq("available_date_destinations"));
        verifyNoMoreInteractions(jdbc);
        verify(veze).deleteForExpiredDatesWithNoBookings(CUTOFF);
        verify(datumi).deleteExpiredWithNoBookings(CUTOFF);
    }

    @Test
    void padBrisanjaTerminaSeNePrecutkuje() {
        staraTabela(0);
        DataIntegrityViolationException fk = new DataIntegrityViolationException("nepoznat FK");
        when(datumi.deleteExpiredWithNoBookings(CUTOFF)).thenThrow(fk);

        assertSame(fk, assertThrows(DataIntegrityViolationException.class,
                () -> cleanup.obrisiIstekleBezRezervacija(CUTOFF)));
    }
}
