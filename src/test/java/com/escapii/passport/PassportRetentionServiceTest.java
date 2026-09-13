package com.escapii.passport;

import com.escapii.model.Booking;
import com.escapii.model.PassengerInfo;
import com.escapii.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Briše se samo broj pasoša; ime, datum rođenja, zemlja i "validan" ostaju. */
class PassportRetentionServiceTest {

    private final BookingRepository repo = mock(BookingRepository.class);
    private final PassportRetentionService svc = new PassportRetentionService(repo);

    @BeforeEach
    void rok() {
        ReflectionTestUtils.setField(svc, "daysAfterReturn", 30);
    }

    private static PassengerInfo putnik(String ime, String pasos) {
        return new PassengerInfo(ime, "F", LocalDate.of(1992, 5, 5), null, true, "Srbija", pasos);
    }

    @Test
    void briseSamoBrojeve_ostaloOstaje() {
        PassengerInfo p1 = putnik("Ana Anić", "BB7654321");
        PassengerInfo p2 = putnik("Mila Milić", null);
        Booking b = new Booking();
        b.setPassengers(new ArrayList<>(List.of(p1, p2)));
        LocalDate danas = LocalDate.of(2026, 9, 13);
        when(repo.findWithPassportsToPurge(danas.minusDays(30), danas)).thenReturn(List.of(b));

        assertEquals(1, svc.purgeExpired(danas));

        assertNull(p1.getPassportNumber());
        assertNull(p2.getPassportNumber());
        assertEquals("Ana Anić", p1.getName());
        assertEquals(LocalDate.of(1992, 5, 5), p1.getDateOfBirth());
        assertEquals("Srbija", p1.getPassportCountry());
        assertEquals(Boolean.TRUE, p1.getHasValidPassport());
        verify(repo).saveAll(List.of(b));
    }

    @Test
    void bezKandidataNistaSeNeCuva() {
        when(repo.findWithPassportsToPurge(any(), any())).thenReturn(List.of());
        assertEquals(0, svc.purgeExpired(LocalDate.of(2026, 9, 13)));
        verify(repo, never()).saveAll(any());
    }

    @Test
    void rokJeKonfigurabilan() {
        ReflectionTestUtils.setField(svc, "daysAfterReturn", 7);
        LocalDate danas = LocalDate.of(2026, 9, 13);
        when(repo.findWithPassportsToPurge(danas.minusDays(7), danas)).thenReturn(List.of());
        svc.purgeExpired(danas);
        verify(repo).findWithPassportsToPurge(danas.minusDays(7), danas);
    }
}
