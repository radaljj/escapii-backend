package com.escapii.service.flow;

import com.escapii.model.CustomDateInquiry;
import com.escapii.repository.CustomDateInquiryRepository;
import com.escapii.service.email.InquiryEmailService;
import com.escapii.service.impl.CustomDateInquiryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Admin mora moći da izmeni traženi datum/broj noćenja na upitu PRE kreiranja
 * privatnog termina - npr. klijent traži 1-5 sept, nema dobrih opcija, admin
 * dogovori 2-7 sept telefonom, pa izmeni upit da createPrivateDateFromInquiry
 * (koji čita desiredDepartureDate/nights direktno sa upita) kreira termin sa
 * ispravnim, dogovorenim datumom.
 */
@ExtendWith(MockitoExtension.class)
class InquiryDateUpdateTest {

    @Mock private CustomDateInquiryRepository inquiryRepository;
    @Mock private InquiryEmailService inquiryEmailService;

    private CustomDateInquiryServiceImpl svc;

    @BeforeEach
    void setUp() {
        svc = new CustomDateInquiryServiceImpl(inquiryRepository, inquiryEmailService);
    }

    private CustomDateInquiry inquiry() {
        CustomDateInquiry i = new CustomDateInquiry();
        i.setId(7L);
        i.setAirport("BEG");
        i.setTravelers(2);
        i.setDesiredDepartureDate(LocalDate.now().plusDays(10));
        i.setNights(4);
        i.setEmail("klijent@example.com");
        return i;
    }

    @Test
    void izmenaDatumaISeNoceniaSeCuva() {
        CustomDateInquiry i = inquiry();
        when(inquiryRepository.findById(7L)).thenReturn(Optional.of(i));
        when(inquiryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDate noviDatum = LocalDate.now().plusDays(15);
        var resp = svc.updateDate(7L, noviDatum, 10);

        assertEquals(noviDatum, resp.getDesiredDepartureDate());
        assertEquals(10, resp.getNights());
        assertEquals(noviDatum, i.getDesiredDepartureDate(), "entitet mora biti stvarno izmenjen, ne samo DTO");
        assertEquals(10, i.getNights());
    }

    @Test
    void datumUProslostiSeOdbija() {
        when(inquiryRepository.findById(7L)).thenReturn(Optional.of(inquiry()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.updateDate(7L, LocalDate.now().minusDays(1), 3));
        assertEquals(400, ex.getStatusCode().value());
        verify(inquiryRepository, never()).save(any());
    }

    /** Gornja granica je 14 noćenja (email prognoze i dalje čitljivo prikazuje i duža putovanja). */
    @Test
    void tacno14NocenjaProlazi() {
        CustomDateInquiry i = inquiry();
        when(inquiryRepository.findById(7L)).thenReturn(Optional.of(i));
        when(inquiryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = svc.updateDate(7L, LocalDate.now().plusDays(5), 14);
        assertEquals(14, resp.getNights());
    }

    @Test
    void nocenjaVanOpsega1Do14SeOdbijaju() {
        when(inquiryRepository.findById(7L)).thenReturn(Optional.of(inquiry()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.updateDate(7L, LocalDate.now().plusDays(5), 15));
        assertEquals(400, ex.getStatusCode().value());
        verify(inquiryRepository, never()).save(any());
    }

    @Test
    void nepostojeciUpitVraca404() {
        when(inquiryRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.updateDate(99L, LocalDate.now().plusDays(5), 2));
        assertEquals(404, ex.getStatusCode().value());
    }
}
