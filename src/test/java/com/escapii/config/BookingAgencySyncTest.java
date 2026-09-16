package com.escapii.config;

import com.escapii.model.Agency;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.service.AppErrorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Pri startu: nefakturisane rezervacije čiji snimak agencije ne odgovara terminu prelaze na agenciju termina. */
class BookingAgencySyncTest {

    private final BookingRepository rezervacije = mock(BookingRepository.class);
    private final AppErrorService greske = mock(AppErrorService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AppErrorService> provider = mock(ObjectProvider.class);

    private BookingAgencySync sync() {
        when(provider.getIfAvailable()).thenReturn(greske);
        when(rezervacije.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        return new BookingAgencySync(rezervacije, mock(PlatformTransactionManager.class), provider);
    }

    private static Agency agencija(long id, String ime) {
        Agency a = new Agency();
        a.setId(id);
        a.setName(ime);
        return a;
    }

    private static Booking naTerminu(long id, String ref, Agency agencijaTermina, Long snimakId, String snimakIme) {
        AvailableDate d = new AvailableDate();
        d.setId(38L);
        d.setAgency(agencijaTermina);
        Booking b = new Booking();
        b.setId(id);
        b.setBookingRef(ref);
        b.setStatus(BookingStatus.COMPLETED);
        b.setSelectedDate(d);
        b.setAgencyIdSnapshot(snimakId);
        b.setAgencyNameSnapshot(snimakIme);
        return b;
    }

    @Test
    void neuskladjene_predjuNaAgencijuTermina() {
        Agency tst = agencija(2, "tst");
        Booking staraAgencija = naTerminu(1, "ESC-aaaa1111", tst, 1L, "Sani Tours");
        Booking bezSnimka     = naTerminu(2, "ESC-bbbb2222", tst, null, null);
        when(rezervacije.findAgencyOutOfSync()).thenReturn(List.of(staraAgencija, bezSnimka));

        assertEquals(2, sync().primeni());

        for (Booking b : List.of(staraAgencija, bezSnimka)) {
            assertEquals(2L, b.getAgencyIdSnapshot(), b.getBookingRef());
            assertEquals("tst", b.getAgencyNameSnapshot(), b.getBookingRef());
            verify(rezervacije).save(b);
        }
        verifyNoInteractions(greske);
    }

    @Test
    void bezNeuskladjenih_nistaNeDira() {
        when(rezervacije.findAgencyOutOfSync()).thenReturn(List.of());

        sync().naStartu();

        verify(rezervacije, never()).save(any());
        verifyNoInteractions(greske);
    }

    @Test
    void greskaPriStartu_ideUAppError_neObaraStart() {
        when(rezervacije.findAgencyOutOfSync()).thenThrow(new RuntimeException("baza nedostupna"));

        BookingAgencySync s = sync();
        assertDoesNotThrow(s::naStartu);

        verify(greske).record(eq("booking-agency-sync"), anyInt(), any(RuntimeException.class));
    }
}
